package in.ggv.campora.companion.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import in.ggv.campora.companion.dto.RideView;
import in.ggv.campora.companion.repository.CompanionRideMemberRepository;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.dto.PassengerView;
import in.ggv.campora.trip.repository.TripRepository;

/**
 * Companion rides: a student with a vehicle offers seats; others join.
 *
 * <p>If a joining student already has an OPEN request on the same route,
 * the seat fulfils that request (it becomes MATCHED), so demand is never
 * counted twice. Joins lock the ride row, so two students can never take
 * the last seat at the same time.</p>
 */
@Service
public class CompanionService {

    private static final Duration MAX_ADVANCE = Duration.ofHours(24);
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    private final CompanionRideRepository rides;
    private final CompanionRideMemberRepository members;
    private final TravelRequestRepository requests;
    private final TripRepository trips;
    private final ProfileService profiles;
    private final CampusService campus;
    private final CamporaProperties properties;
    private final Clock clock;

    public CompanionService(CompanionRideRepository rides, CompanionRideMemberRepository members,
                            TravelRequestRepository requests, TripRepository trips, ProfileService profiles,
                            CampusService campus, CamporaProperties properties, Clock clock) {
        this.rides = rides;
        this.members = members;
        this.requests = requests;
        this.trips = trips;
        this.profiles = profiles;
        this.campus = campus;
        this.properties = properties;
        this.clock = clock;
    }

    // ── host actions ───────────────────────────────────────────

    @Transactional
    public UUID offer(UUID userId, UUID pickupId, UUID destinationId, LocalDateTime departureLocal,
                      int seats, String note) {
        StudentProfile host = profiles.requireStudent(userId);
        if (rides.existsByHostIdAndStatusIn(host.getId(), RideStatus.ACTIVE)) {
            throw new BusinessException("You are already hosting a ride.");
        }
        if (requests.existsByStudentIdAndStatusIn(host.getId(), RequestStatus.ACTIVE)) {
            throw new BusinessException("Cancel your own travel request before offering a ride.");
        }
        if (isRidingWithSomeone(host)) {
            throw new BusinessException("You have joined another companion ride.");
        }
        if (pickupId != null && pickupId.equals(destinationId)) {
            throw new BusinessException("Pickup and destination must be different.");
        }
        int maxSeats = properties.companion().maxSeats();
        if (seats < 1 || seats > maxSeats) {
            throw new BusinessException("You can offer between 1 and " + maxSeats + " seats.");
        }
        if (departureLocal == null) {
            throw new BusinessException("Choose a departure time.");
        }
        Instant now = clock.instant();
        Instant departure = departureLocal.atZone(properties.zoneId()).toInstant();
        if (departure.isBefore(now.minus(PAST_TOLERANCE)) || departure.isAfter(now.plus(MAX_ADVANCE))) {
            throw new BusinessException("Departure must be within the next 24 hours.");
        }
        Destination pickup = campus.requireActiveLocation(pickupId);
        Destination destination = campus.requireActiveLocation(destinationId);
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        return rides.save(new CompanionRide(host, pickup, destination, departure, seats, cleanNote)).getId();
    }

    @Transactional
    public void start(UUID userId, UUID rideId) {
        CompanionRide ride = lockHostedRide(userId, rideId);
        ride.start();
        Trip trip = Trip.companion(ride, ride.bookedSeats());
        trip.start(clock.instant());
        trips.save(trip);
        linkedRequests(ride, RequestStatus.MATCHED).forEach(TravelRequest::markInTrip);
    }

    @Transactional
    public void complete(UUID userId, UUID rideId) {
        CompanionRide ride = lockHostedRide(userId, rideId);
        ride.complete();
        Instant now = clock.instant();
        trips.findByCompanionRideId(ride.getId()).ifPresent(trip -> trip.complete(now));
        linkedRequests(ride, RequestStatus.IN_TRIP).forEach(TravelRequest::markCompleted);
    }

    @Transactional
    public void cancel(UUID userId, UUID rideId) {
        CompanionRide ride = lockHostedRide(userId, rideId);
        ride.cancel();
        Instant reopenUntil = clock.instant().plus(TravelRequest.REOPEN_GRACE);
        for (CompanionRideMember member : members.findByRideIdAndStatus(ride.getId(), MemberStatus.JOINED)) {
            member.leave();
            reopenLinkedRequest(member, reopenUntil);
        }
    }

    // ── rider actions ──────────────────────────────────────────

    @Transactional
    public void join(UUID userId, UUID rideId, int seats) {
        StudentProfile student = profiles.requireStudent(userId);
        CompanionRide ride = rides.findByIdForUpdate(rideId).orElseThrow(() -> new NotFoundException("Ride"));
        Instant now = clock.instant();

        if (ride.isHostedBy(student)) {
            throw new BusinessException("You cannot join your own ride.");
        }
        if (ride.getDepartureTime().isBefore(now.minus(properties.companion().joinGrace()))) {
            throw new BusinessException("This ride has already left.");
        }
        if (isRidingWithSomeone(student)) {
            throw new BusinessException("You have already joined a companion ride.");
        }
        if (rides.existsByHostIdAndStatusIn(student.getId(), RideStatus.ACTIVE)) {
            throw new BusinessException("You are hosting a ride of your own.");
        }

        TravelRequest linked = null;
        int seatCount = seats;
        List<TravelRequest> active = requests.findByStudentAndStatuses(student.getId(), RequestStatus.ACTIVE);
        if (!active.isEmpty()) {
            TravelRequest own = active.get(0);
            boolean sameRoute = own.getPickup().getId().equals(ride.getPickup().getId())
                    && own.getDestination().getId().equals(ride.getDestination().getId());
            if (own.getStatus() != RequestStatus.OPEN || !sameRoute) {
                throw new BusinessException("You already have an active request for another ride. Cancel it first.");
            }
            linked = requests.findByIdForUpdate(own.getId()).orElseThrow(() -> new NotFoundException("Request"));
            seatCount = linked.getSeatsRequired();
        }
        if (seatCount < 1 || seatCount > properties.companion().maxSeats()) {
            throw new BusinessException("Choose a valid number of seats.");
        }

        ride.takeSeats(seatCount);
        if (linked != null) {
            linked.markMatched();
        }
        members.save(new CompanionRideMember(ride, student, seatCount, linked));
    }

    @Transactional
    public void leave(UUID userId, UUID rideId) {
        StudentProfile student = profiles.requireStudent(userId);
        CompanionRide ride = rides.findByIdForUpdate(rideId).orElseThrow(() -> new NotFoundException("Ride"));
        CompanionRideMember member = members
                .findByRideIdAndStudentIdAndStatus(rideId, student.getId(), MemberStatus.JOINED)
                .orElseThrow(() -> new NotFoundException("Membership"));
        if (!RideStatus.JOINABLE_OR_FULL.contains(ride.getStatus())) {
            throw new BusinessException("The ride has already started.");
        }
        member.leave();
        ride.returnSeats(member.getSeats());
        reopenLinkedRequest(member, clock.instant().plus(TravelRequest.REOPEN_GRACE));
    }

    // ── read side ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RideView> joinableRides(UUID userId) {
        StudentProfile student = profiles.requireStudent(userId);
        Instant after = clock.instant().minus(properties.companion().joinGrace());
        Optional<TravelRequest> openRequest = requests
                .findByStudentAndStatuses(student.getId(), EnumSet.of(RequestStatus.OPEN)).stream().findFirst();

        return rides.findJoinable(RideStatus.OPEN, after, student.getId()).stream()
                .map(ride -> {
                    boolean matches = openRequest
                            .map(r -> r.getPickup().getId().equals(ride.getPickup().getId())
                                    && r.getDestination().getId().equals(ride.getDestination().getId()))
                            .orElse(false);
                    return toView(ride, null, matches, List.of());
                })
                .sorted((a, b) -> Boolean.compare(b.matchesMyRoute(), a.matchesMyRoute()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<RideView> hostedRide(UUID userId) {
        StudentProfile host = profiles.requireStudent(userId);
        return rides.findByHostAndStatuses(host.getId(), RideStatus.ACTIVE).stream().findFirst()
                .map(ride -> toView(ride, null, false, riders(ride)));
    }

    @Transactional(readOnly = true)
    public Optional<RideView> joinedRide(UUID userId) {
        StudentProfile student = profiles.requireStudent(userId);
        return members.findActiveForStudent(student.getId(), MemberStatus.JOINED, RideStatus.ACTIVE).stream()
                .findFirst()
                .map(m -> toView(m.getRide(), m.getRide().getHost().getUser().getMobileNumber(), false, List.of()));
    }

    // ── internals ──────────────────────────────────────────────

    private CompanionRide lockHostedRide(UUID userId, UUID rideId) {
        StudentProfile host = profiles.requireStudent(userId);
        CompanionRide ride = rides.findByIdForUpdate(rideId).orElseThrow(() -> new NotFoundException("Ride"));
        if (!ride.isHostedBy(host)) {
            throw new NotFoundException("Ride");
        }
        return ride;
    }

    private boolean isRidingWithSomeone(StudentProfile student) {
        return !members.findActiveForStudent(student.getId(), MemberStatus.JOINED, RideStatus.ACTIVE).isEmpty();
    }

    private List<TravelRequest> linkedRequests(CompanionRide ride, RequestStatus status) {
        return members.findByRideIdAndStatus(ride.getId(), MemberStatus.JOINED).stream()
                .map(CompanionRideMember::getTravelRequest)
                .filter(r -> r != null && r.getStatus() == status)
                .toList();
    }

    private void reopenLinkedRequest(CompanionRideMember member, Instant reopenUntil) {
        TravelRequest request = member.getTravelRequest();
        if (request != null && request.getStatus() == RequestStatus.MATCHED) {
            request.reopen(reopenUntil);
        }
    }

    private List<PassengerView> riders(CompanionRide ride) {
        return members.findByRideWithStudents(ride.getId(), MemberStatus.JOINED).stream()
                .map(m -> {
                    User user = m.getStudent().getUser();
                    return new PassengerView(user.getFullName(), user.getMobileNumber(), m.getSeats());
                })
                .toList();
    }

    private static RideView toView(CompanionRide ride, String hostMobile, boolean matches, List<PassengerView> riders) {
        return new RideView(ride.getId(), ride.getHost().getUser().getFullName(), hostMobile,
                ride.getPickup().getName(), ride.getDestination().getName(), ride.getDepartureTime(),
                ride.getTotalSeats(), ride.getAvailableSeats(), ride.getStatus(), ride.getNote(), matches, riders);
    }
}
