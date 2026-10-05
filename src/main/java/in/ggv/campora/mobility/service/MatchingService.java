package in.ggv.campora.mobility.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.mobility.domain.DriverResponse;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.dto.HotspotView;
import in.ggv.campora.mobility.dto.ReservationResult;
import in.ggv.campora.mobility.repository.DriverResponseRepository;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;

/**
 * <h2>The Demand Engine</h2>
 *
 * <p>Campora is demand-oriented, not booking-oriented. Instead of one auto
 * per student, open requests on the same (pickup → destination) route form a
 * live <em>hotspot</em>. Drivers see only anonymous hotspot totals and
 * reserve seats against them; each reservation absorbs the oldest requests
 * that fit, and when no demand is left the hotspot simply disappears.</p>
 *
 * <pre>
 *   10 students ─► hotspot (10 seats) ─► Driver A reserves 5 ─► 5 left
 *                                      ─► Driver B reserves 5 ─► closed
 * </pre>
 *
 * <p><b>Consistency.</b> Reservation locks the route's open rows
 * ({@code SELECT … FOR UPDATE}); concurrent drivers queue on that lock, and
 * the second one sees only what the first left behind. Rows also carry an
 * optimistic {@code @Version}, and on PostgreSQL a partial unique index
 * guarantees at most one ACCEPTED response per request.</p>
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final TravelRequestRepository requests;
    private final DriverResponseRepository responses;
    private final DriverProfileRepository drivers;
    private final TripRepository trips;
    private final Clock clock;

    public MatchingService(TravelRequestRepository requests, DriverResponseRepository responses,
                           DriverProfileRepository drivers, TripRepository trips, Clock clock) {
        this.requests = requests;
        this.responses = responses;
        this.drivers = drivers;
        this.trips = trips;
        this.clock = clock;
    }

    /** Live hotspots, largest demand first. Contains no student identities. */
    @Transactional(readOnly = true)
    public List<HotspotView> hotspots() {
        return requests.findHotspots(RequestStatus.OPEN, RequestType.COMPANION, clock.instant());
    }

    /**
     * Reserve up to {@code seats} seats on a route for the signed-in driver.
     * Creates one Trip plus one accepted DriverResponse per absorbed request.
     */
    @Transactional
    public ReservationResult reserveSeats(UUID driverUserId, UUID pickupId, UUID destinationId, int seats) {
        DriverProfile driver = drivers.findByUserIdForUpdate(driverUserId)
                .orElseThrow(() -> new NotFoundException("Driver profile"));
        assertCanReserve(driver, seats);

        Instant now = clock.instant();
        List<TravelRequest> open = new ArrayList<>(requests.lockOpenForRoute(
                pickupId, destinationId, RequestStatus.OPEN, RequestType.COMPANION, now));
        // Re-check after acquiring the locks: a competing driver may have
        // matched some rows while we waited (databases differ on whether the
        // WHERE clause is re-evaluated after a lock wait).
        open.removeIf(r -> r.getStatus() != RequestStatus.OPEN);
        if (open.isEmpty()) {
            throw new BusinessException("This hotspot has just closed — no open demand is left on that route.");
        }

        List<TravelRequest> chosen = SeatAllocator.allocate(open, seats, TravelRequest::getSeatsRequired);
        if (chosen.isEmpty()) {
            throw new BusinessException("The waiting groups need more seats than you offered. Try offering more seats.");
        }

        int filled = chosen.stream().mapToInt(TravelRequest::getSeatsRequired).sum();
        TravelRequest first = chosen.get(0);
        Trip trip = trips.save(Trip.auto(driver, first.getPickup(), first.getDestination(), filled));

        List<DriverResponse> accepted = new ArrayList<>(chosen.size());
        for (TravelRequest request : chosen) {
            request.markMatched();
            accepted.add(new DriverResponse(request, driver, trip));
        }
        responses.saveAll(accepted);

        long totalDemand = open.stream().mapToLong(TravelRequest::getSeatsRequired).sum();
        long stillWaiting = totalDemand - filled;
        log.info("Driver {} reserved {} seat(s) for {} request(s) on {} → {}; {} seat(s) still waiting",
                driver.getId(), filled, chosen.size(), pickupId, destinationId, stillWaiting);
        return new ReservationResult(trip.getId(), filled, chosen.size(), stillWaiting);
    }

    private void assertCanReserve(DriverProfile driver, int seats) {
        if (!driver.isVerified()) {
            throw new BusinessException("Your account is awaiting verification by the transport office.");
        }
        if (!driver.isOnline()) {
            throw new BusinessException("Go online to accept rides.");
        }
        if (seats < 1 || seats > driver.getSeatCapacity()) {
            throw new BusinessException("You can offer between 1 and " + driver.getSeatCapacity() + " seats.");
        }
        if (trips.existsByDriverIdAndStatusIn(driver.getId(), TripStatus.ACTIVE)) {
            throw new BusinessException("Finish your current trip before taking another one.");
        }
    }
}
