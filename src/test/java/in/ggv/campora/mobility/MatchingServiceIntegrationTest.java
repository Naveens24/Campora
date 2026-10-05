package in.ggv.campora.mobility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRepository;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.RoleRepository;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.dto.HotspotView;
import in.ggv.campora.mobility.dto.ReservationResult;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.mobility.service.MatchingService;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;
import in.ggv.campora.trip.service.TripService;

/**
 * End-to-end demand-engine behaviour against a real (H2) database:
 * clustering, seat reservation, hotspot closing, cancellation and races.
 * Each test builds its own route so tests never share demand.
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchingServiceIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired MatchingService matching;
    @Autowired TripService trips;
    @Autowired TravelRequestRepository requests;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired StudentProfileRepository students;
    @Autowired DriverProfileRepository drivers;
    @Autowired DepartmentRepository departments;
    @Autowired DestinationRepository destinations;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    private Destination pickup;
    private Destination destination;

    @BeforeEach
    void freshRoute() {
        int n = SEQ.incrementAndGet();
        pickup = destinations.save(new Destination("Test Gate " + n, null, LocationType.CAMPUS_POINT, 1));
        destination = destinations.save(new Destination("Test Market " + n, null, LocationType.CITY, 1));
    }

    @Test
    void tenSeatsOfDemandAreAbsorbedByTwoDriversAndTheHotspotCloses() {
        for (int seats : new int[] {2, 2, 1, 2, 2, 1}) {
            raiseRequest(seats);
        }
        assertThat(hotspotOnRoute().seatsDemanded()).isEqualTo(10L);
        assertThat(hotspotOnRoute().requests()).isEqualTo(6L);

        ReservationResult a = matching.reserveSeats(driverUser(5), pickup.getId(), destination.getId(), 5);
        assertThat(a.seatsFilled()).isEqualTo(5);
        assertThat(a.requestsMatched()).isEqualTo(3);
        assertThat(a.seatsStillWaiting()).isEqualTo(5);

        ReservationResult b = matching.reserveSeats(driverUser(5), pickup.getId(), destination.getId(), 5);
        assertThat(b.seatsFilled()).isEqualTo(5);
        assertThat(b.hotspotClosed()).isTrue();

        assertThat(matching.hotspots()).noneMatch(this::onRoute);
        UUID lateDriver = driverUser(5);
        assertThatThrownBy(() -> matching.reserveSeats(lateDriver, pickup.getId(), destination.getId(), 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void cancelledTripPutsStudentsBackOnTheBoard() {
        raiseRequest(2);
        raiseRequest(1);
        UUID driver = driverUser(4);
        ReservationResult result = matching.reserveSeats(driver, pickup.getId(), destination.getId(), 4);
        assertThat(matching.hotspots()).noneMatch(this::onRoute);

        trips.cancel(driver, result.tripId());

        assertThat(hotspotOnRoute().seatsDemanded()).isEqualTo(3L);
    }

    @Test
    void companionOnlyRequestsAreNotShownToAutoDrivers() {
        raiseRequest(1, RequestType.COMPANION);
        assertThat(matching.hotspots()).noneMatch(this::onRoute);
    }

    @Test
    void driverCannotHoldTwoTripsAtOnce() {
        raiseRequest(1);
        raiseRequest(1);
        UUID driver = driverUser(1);
        matching.reserveSeats(driver, pickup.getId(), destination.getId(), 1);
        assertThatThrownBy(() -> matching.reserveSeats(driver, pickup.getId(), destination.getId(), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentDriversNeverShareARequest() throws Exception {
        List<UUID> requestIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requestIds.add(raiseRequest(1));
        }
        UUID d1 = driverUser(4);
        UUID d2 = driverUser(4);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> jobs = List.of(() -> reserveOrZero(d1), () -> reserveOrZero(d2));
            int filled = 0;
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                filled += f.get();
            }
            long matched = requestIds.stream()
                    .map(id -> requests.findById(id).orElseThrow().getStatus())
                    .filter(s -> s == RequestStatus.MATCHED)
                    .count();
            // Every seat handed out corresponds to exactly one matched request — no double booking.
            assertThat(matched).isEqualTo(filled);
            assertThat(filled).isBetween(4, 6);
        } finally {
            pool.shutdownNow();
        }
    }

    // ── fixtures ─────────────────────────────────────────────────

    private int reserveOrZero(UUID driver) {
        try {
            return matching.reserveSeats(driver, pickup.getId(), destination.getId(), 4).seatsFilled();
        } catch (RuntimeException raceLost) {
            return 0;
        }
    }

    private boolean onRoute(HotspotView h) {
        return h.pickupId().equals(pickup.getId()) && h.destinationId().equals(destination.getId());
    }

    private HotspotView hotspotOnRoute() {
        return matching.hotspots().stream().filter(this::onRoute).findFirst().orElseThrow();
    }

    private UUID raiseRequest(int seats) {
        return raiseRequest(seats, RequestType.ANY);
    }

    private UUID raiseRequest(int seats, RequestType type) {
        User user = user(RoleName.STUDENT);
        StudentProfile student = students.save(new StudentProfile(user,
                departments.findByCode("MCA").orElseThrow(), "T/" + user.getMobileNumber(), 3));
        return requests.save(new TravelRequest(student, pickup, destination, type, seats,
                clock.instant().plus(Duration.ofMinutes(30)))).getId();
    }

    private UUID driverUser(int capacity) {
        User user = user(RoleName.DRIVER);
        DriverProfile driver = new DriverProfile(user, VehicleType.AUTO, "CG" + user.getMobileNumber(), capacity, null);
        driver.verify();
        driver.setAvailability(Availability.ONLINE);
        drivers.save(driver);
        return user.getId();
    }

    private User user(RoleName role) {
        String mobile = "7" + String.format("%09d", SEQ.incrementAndGet() * 7L);
        User user = new User("Test", role.name(), mobile, encoder.encode("password1"));
        user.markMobileVerified(clock.instant());
        user.assignRole(roles.findByName(role).orElseThrow());
        return users.save(user);
    }
}
