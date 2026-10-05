package in.ggv.campora.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.admin.dto.AdminStats;
import in.ggv.campora.admin.dto.DriverRow;
import in.ggv.campora.campus.domain.DestinationRequestStatus;
import in.ggv.campora.campus.repository.DestinationRequestRepository;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.companion.domain.RideStatus;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;

/** Transport-office console: platform health and driver verification. */
@Service
public class AdminService {

    private final UserRepository users;
    private final DriverProfileRepository drivers;
    private final TravelRequestRepository requests;
    private final TripRepository trips;
    private final CompanionRideRepository rides;
    private final DestinationRequestRepository proposals;
    private final ZoneId zone;
    private final Clock clock;

    public AdminService(UserRepository users, DriverProfileRepository drivers, TravelRequestRepository requests,
                        TripRepository trips, CompanionRideRepository rides,
                        DestinationRequestRepository proposals, CamporaProperties properties, Clock clock) {
        this.users = users;
        this.drivers = drivers;
        this.requests = requests;
        this.trips = trips;
        this.rides = rides;
        this.proposals = proposals;
        this.zone = properties.zoneId();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        Instant startOfDay = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return new AdminStats(
                users.countByRole(RoleName.STUDENT),
                users.countByRole(RoleName.DRIVER),
                drivers.countByVerifiedFalse(),
                drivers.countByAvailabilityAndVerifiedTrue(Availability.ONLINE),
                requests.countByStatus(RequestStatus.OPEN),
                trips.countByStatusIn(TripStatus.ACTIVE),
                trips.countByStatusAndEndTimeAfter(TripStatus.COMPLETED, startOfDay),
                rides.countByStatusIn(RideStatus.ACTIVE),
                proposals.countByStatus(DestinationRequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<DriverRow> drivers() {
        return drivers.findAllWithUser().stream()
                .map(d -> new DriverRow(d.getId(), d.getUser().getFullName(), d.getUser().getMobileNumber(),
                        d.getVehicleType().getLabel(), d.getVehicleNumber(), d.getSeatCapacity(),
                        d.getNativeArea(), d.isVerified(), d.getAvailability(), d.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void verifyDriver(UUID driverId) {
        requireDriver(driverId).verify();
    }

    @Transactional
    public void revokeDriver(UUID driverId) {
        requireDriver(driverId).revokeVerification();
    }

    private DriverProfile requireDriver(UUID id) {
        return drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver"));
    }
}
