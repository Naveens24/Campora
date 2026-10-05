package in.ggv.campora.trip.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.mobility.domain.DriverResponse;
import in.ggv.campora.mobility.domain.ResponseStatus;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.repository.DriverResponseRepository;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.dto.DriverTripView;
import in.ggv.campora.trip.dto.PassengerView;
import in.ggv.campora.trip.repository.TripRepository;

/** Driver-side trip lifecycle: start, complete, cancel, and history. */
@Service
public class TripService {

    private final TripRepository trips;
    private final DriverResponseRepository responses;
    private final ProfileService profiles;
    private final Clock clock;

    public TripService(TripRepository trips, DriverResponseRepository responses, ProfileService profiles, Clock clock) {
        this.trips = trips;
        this.responses = responses;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<DriverTripView> activeTrip(UUID driverUserId) {
        DriverProfile driver = profiles.requireDriver(driverUserId);
        return trips.findByDriverAndStatuses(driver.getId(), TripStatus.ACTIVE).stream()
                .findFirst()
                .map(trip -> toView(trip, passengers(trip.getId())));
    }

    @Transactional(readOnly = true)
    public List<DriverTripView> history(UUID driverUserId, int limit) {
        DriverProfile driver = profiles.requireDriver(driverUserId);
        return trips.findDriverHistory(driver.getId(), PageRequest.of(0, limit)).stream()
                .map(trip -> toView(trip, List.of()))
                .toList();
    }

    /** Driver has picked everyone up. Every passenger's request moves to IN_TRIP. */
    @Transactional
    public void start(UUID driverUserId, UUID tripId) {
        Trip trip = lockOwnedTrip(driverUserId, tripId);
        trip.start(clock.instant());
        acceptedResponses(trip).forEach(r -> r.getRequest().markInTrip());
    }

    @Transactional
    public void complete(UUID driverUserId, UUID tripId) {
        Trip trip = lockOwnedTrip(driverUserId, tripId);
        trip.complete(clock.instant());
        acceptedResponses(trip).forEach(r -> r.getRequest().markCompleted());
    }

    /** Driver backs out before departure: seats are released and requests return to the hotspot. */
    @Transactional
    public void cancel(UUID driverUserId, UUID tripId) {
        Trip trip = lockOwnedTrip(driverUserId, tripId);
        Instant now = clock.instant();
        trip.cancel(now);
        for (DriverResponse response : acceptedResponses(trip)) {
            response.cancel();
            TravelRequest request = response.getRequest();
            request.reopen(now.plus(TravelRequest.REOPEN_GRACE));
        }
    }

    // ── internals ──────────────────────────────────────────────

    private Trip lockOwnedTrip(UUID driverUserId, UUID tripId) {
        DriverProfile driver = profiles.requireDriver(driverUserId);
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> new NotFoundException("Trip"));
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new NotFoundException("Trip");
        }
        return trip;
    }

    private List<DriverResponse> acceptedResponses(Trip trip) {
        return responses.findByTripIdAndStatus(trip.getId(), ResponseStatus.ACCEPTED);
    }

    private List<PassengerView> passengers(UUID tripId) {
        return responses.findByTripWithPassengers(tripId, ResponseStatus.ACCEPTED).stream()
                .map(r -> {
                    User user = r.getRequest().getStudent().getUser();
                    return new PassengerView(user.getFullName(), user.getMobileNumber(), r.getSeatsReserved());
                })
                .toList();
    }

    private static DriverTripView toView(Trip trip, List<PassengerView> passengers) {
        return new DriverTripView(trip.getId(), trip.getPickup().getName(), trip.getDestination().getName(),
                trip.getSeatsReserved(), trip.getStatus(), trip.getCreatedAt(), trip.getStartTime(),
                trip.getEndTime(), passengers);
    }
}
