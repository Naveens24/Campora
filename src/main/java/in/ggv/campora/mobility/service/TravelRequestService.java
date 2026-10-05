package in.ggv.campora.mobility.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.companion.domain.CompanionRideMember;
import in.ggv.campora.companion.domain.MemberStatus;
import in.ggv.campora.companion.domain.RideStatus;
import in.ggv.campora.companion.repository.CompanionRideMemberRepository;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.mobility.domain.DriverResponse;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.domain.ResponseStatus;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.dto.MatchInfo;
import in.ggv.campora.mobility.dto.RequestHistoryRow;
import in.ggv.campora.mobility.dto.StudentRequestView;
import in.ggv.campora.mobility.repository.DriverResponseRepository;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;

/**
 * The student side of mobility: raise, view and cancel travel requests.
 *
 * <p>Lock order everywhere in the app is <em>trip/ride → request</em>, so
 * a student cancelling and a driver starting the same trip cannot deadlock.</p>
 */
@Service
public class TravelRequestService {

    private final TravelRequestRepository requests;
    private final DriverResponseRepository responses;
    private final TripRepository trips;
    private final CompanionRideRepository rides;
    private final CompanionRideMemberRepository members;
    private final ProfileService profiles;
    private final CampusService campus;
    private final CamporaProperties.Mobility config;
    private final Clock clock;

    public TravelRequestService(TravelRequestRepository requests, DriverResponseRepository responses,
                                TripRepository trips, CompanionRideRepository rides,
                                CompanionRideMemberRepository members, ProfileService profiles,
                                CampusService campus, CamporaProperties properties, Clock clock) {
        this.requests = requests;
        this.responses = responses;
        this.trips = trips;
        this.rides = rides;
        this.members = members;
        this.profiles = profiles;
        this.campus = campus;
        this.config = properties.mobility();
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID userId, UUID pickupId, UUID destinationId, RequestType type, int seats) {
        StudentProfile student = profiles.requireStudent(userId);
        assertFreeToRequest(student);

        if (pickupId != null && pickupId.equals(destinationId)) {
            throw new BusinessException("Pickup and destination must be different.");
        }
        if (seats < 1 || seats > config.maxSeatsPerRequest()) {
            throw new BusinessException("You can request between 1 and " + config.maxSeatsPerRequest() + " seats.");
        }
        Destination pickup = campus.requireActiveLocation(pickupId);
        Destination destination = campus.requireActiveLocation(destinationId);
        RequestType requestType = type == null ? RequestType.ANY : type;

        Instant expiresAt = clock.instant().plus(config.requestTtl());
        return requests.save(new TravelRequest(student, pickup, destination, requestType, seats, expiresAt)).getId();
    }

    @Transactional
    public void cancel(UUID userId, UUID requestId) {
        StudentProfile student = profiles.requireStudent(userId);
        TravelRequest request = requests.findById(requestId)
                .filter(r -> r.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new NotFoundException("Request"));
        Instant now = clock.instant();

        if (request.getStatus() == RequestStatus.MATCHED) {
            responses.findByRequestWithDriver(requestId, ResponseStatus.ACCEPTED)
                    .ifPresent(response -> releaseFromTrip(response, now));
            members.findByTravelRequestIdAndStatus(requestId, MemberStatus.JOINED)
                    .ifPresent(this::releaseFromRide);
        }
        requests.findByIdForUpdate(requestId).orElseThrow(() -> new NotFoundException("Request")).cancel();
    }

    @Transactional(readOnly = true)
    public Optional<StudentRequestView> activeRequest(UUID userId) {
        StudentProfile student = profiles.requireStudent(userId);
        List<TravelRequest> active = requests.findByStudentAndStatuses(student.getId(), RequestStatus.ACTIVE);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        TravelRequest r = active.get(0);
        long others = 0;
        if (r.getStatus() == RequestStatus.OPEN) {
            long onRoute = requests.countOnRoute(r.getPickup().getId(), r.getDestination().getId(),
                    RequestStatus.OPEN, clock.instant());
            others = Math.max(0, onRoute - 1);
        }
        MatchInfo match = r.getStatus() == RequestStatus.OPEN ? null : findMatch(r.getId());
        return Optional.of(new StudentRequestView(r.getId(), r.getPickup().getName(), r.getDestination().getName(),
                r.getRequestType(), r.getSeatsRequired(), r.getStatus(), r.getCreatedAt(), r.getExpiresAt(),
                others, match));
    }

    @Transactional(readOnly = true)
    public List<RequestHistoryRow> history(UUID userId, int limit) {
        StudentProfile student = profiles.requireStudent(userId);
        return requests.findHistory(student.getId(), PageRequest.of(0, limit)).stream()
                .map(r -> new RequestHistoryRow(r.getPickup().getName(), r.getDestination().getName(),
                        r.getRequestType(), r.getSeatsRequired(), r.getStatus(), r.getCreatedAt()))
                .toList();
    }

    /** Expires OPEN requests past their TTL. Called by {@link RequestExpiryJob}. */
    @Transactional
    public int expireStale() {
        return requests.expireStale(RequestStatus.OPEN, RequestStatus.EXPIRED, clock.instant());
    }

    // ── internals ──────────────────────────────────────────────

    private void assertFreeToRequest(StudentProfile student) {
        if (requests.existsByStudentIdAndStatusIn(student.getId(), RequestStatus.ACTIVE)) {
            throw new BusinessException("You already have an active request. Cancel it to raise a new one.");
        }
        if (rides.existsByHostIdAndStatusIn(student.getId(), RideStatus.ACTIVE)) {
            throw new BusinessException("You are hosting a companion ride right now.");
        }
        if (!members.findActiveForStudent(student.getId(), MemberStatus.JOINED, RideStatus.ACTIVE).isEmpty()) {
            throw new BusinessException("You have already joined a companion ride.");
        }
    }

    private void releaseFromTrip(DriverResponse response, Instant now) {
        Trip trip = trips.findByIdForUpdate(response.getTrip().getId())
                .orElseThrow(() -> new NotFoundException("Trip"));
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new BusinessException("Your ride has already started and can no longer be cancelled.");
        }
        response.cancel();
        trip.releaseSeats(response.getSeatsReserved());
        if (responses.countByTripIdAndStatus(trip.getId(), ResponseStatus.ACCEPTED) == 0) {
            trip.cancel(now);
        }
    }

    private void releaseFromRide(CompanionRideMember member) {
        CompanionRide ride = rides.findByIdForUpdate(member.getRide().getId())
                .orElseThrow(() -> new NotFoundException("Ride"));
        if (!RideStatus.JOINABLE_OR_FULL.contains(ride.getStatus())) {
            throw new BusinessException("Your ride has already started and can no longer be cancelled.");
        }
        member.leave();
        ride.returnSeats(member.getSeats());
    }

    private MatchInfo findMatch(UUID requestId) {
        Optional<DriverResponse> auto = responses.findByRequestWithDriver(requestId, ResponseStatus.ACCEPTED);
        if (auto.isPresent()) {
            DriverProfile driver = auto.get().getDriver();
            User user = driver.getUser();
            Trip trip = auto.get().getTrip();
            String detail = trip.getStatus() == TripStatus.IN_PROGRESS ? "On the way" : "Driver is heading to pickup";
            return new MatchInfo("AUTO", user.getFullName(), user.getMobileNumber(),
                    driver.getVehicleType().getLabel() + " · " + driver.getVehicleNumber(), detail);
        }
        return members.findByTravelRequestIdAndStatus(requestId, MemberStatus.JOINED)
                .map(m -> {
                    CompanionRide ride = m.getRide();
                    User host = ride.getHost().getUser();
                    return new MatchInfo("COMPANION", host.getFullName(), host.getMobileNumber(), null,
                            ride.getStatus() == RideStatus.STARTED ? "On the way" : "Companion ride");
                })
                .orElse(null);
    }
}
