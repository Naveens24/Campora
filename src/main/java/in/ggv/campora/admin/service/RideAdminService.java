package in.ggv.campora.admin.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.admin.dto.LiveRides;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.companion.domain.CompanionRideMember;
import in.ggv.campora.companion.domain.MemberStatus;
import in.ggv.campora.companion.domain.RideStatus;
import in.ggv.campora.companion.repository.CompanionRideMemberRepository;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.repository.TravelRequestRepository;
import in.ggv.campora.mobility.service.TravelRequestService;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.domain.TripStatus;
import in.ggv.campora.trip.repository.TripRepository;
import in.ggv.campora.trip.service.TripService;

/**
 * Lets an admin close rides that people forgot or cannot close themselves.
 * Everything goes through the same services the drivers and students use, so
 * passengers are released and requests reopened exactly as usual.
 */
@Service
@Transactional
public class RideAdminService {

    private static final int MAX_WAITING_SHOWN = 100;

    private final TripRepository trips;
    private final TripService tripService;
    private final CompanionRideRepository rides;
    private final CompanionRideMemberRepository members;
    private final CompanionService companions;
    private final TravelRequestRepository requests;
    private final TravelRequestService requestService;
    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;
    private final AdminGuard guard;
    private final AdminLog log;

    public RideAdminService(TripRepository trips, TripService tripService, CompanionRideRepository rides,
                            CompanionRideMemberRepository members, CompanionService companions,
                            TravelRequestRepository requests, TravelRequestService requestService,
                            StudentProfileRepository students, DriverProfileRepository drivers,
                            AdminGuard guard, AdminLog log) {
        this.trips = trips;
        this.tripService = tripService;
        this.rides = rides;
        this.members = members;
        this.companions = companions;
        this.requests = requests;
        this.requestService = requestService;
        this.students = students;
        this.drivers = drivers;
        this.guard = guard;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public LiveRides live() {
        return new LiveRides(
                trips.findDriverTrips(TripStatus.ACTIVE).stream().map(RideAdminService::row).toList(),
                rides.findByStatuses(RideStatus.ACTIVE).stream().map(RideAdminService::row).toList(),
                requests.findByStatusWithStudent(RequestStatus.OPEN, PageRequest.of(0, MAX_WAITING_SHOWN)).stream()
                        .map(RideAdminService::row).toList());
    }

    /** What one person is part of right now. */
    @Transactional(readOnly = true)
    public LiveRides liveFor(UUID userId) {
        List<LiveRides.Row> trip = drivers.findByUserId(userId)
                .map(d -> trips.findByDriverAndStatuses(d.getId(), TripStatus.ACTIVE).stream()
                        .map(RideAdminService::row).toList())
                .orElse(List.of());
        StudentProfile student = students.findByUserId(userId).orElse(null);
        if (student == null) {
            return new LiveRides(trip, List.of(), List.of());
        }
        List<LiveRides.Row> hosted = rides.findByHostAndStatuses(student.getId(), RideStatus.ACTIVE).stream()
                .map(RideAdminService::row).toList();
        List<LiveRides.Row> joined = joined(student).stream().map(m -> row(m.getRide())).toList();
        return new LiveRides(trip,
                Stream.concat(hosted.stream(), joined.stream()).toList(),
                requests.findByStudentAndStatuses(student.getId(), RequestStatus.ACTIVE).stream()
                        .map(RideAdminService::row).toList());
    }

    /** Completes a trip under way, or cancels one that has not left. */
    public void endTrip(UUID adminId, UUID tripId) {
        User admin = guard.admin(adminId);
        Trip trip = trips.findById(tripId).filter(t -> t.getDriver() != null)
                .orElseThrow(() -> new NotFoundException("Trip"));
        User driver = trip.getDriver().getUser();
        end(driver.getId(), trip);
        log.record(admin, driver, "Ended a driver trip", route(trip.getPickup().getName(), trip.getDestination().getName()));
    }

    public void endCompanionRide(UUID adminId, UUID rideId) {
        User admin = guard.admin(adminId);
        CompanionRide ride = rides.findById(rideId).orElseThrow(() -> new NotFoundException("Ride"));
        User host = ride.getHost().getUser();
        end(host.getId(), ride);
        log.record(admin, host, "Ended a companion ride", route(ride.getPickup().getName(), ride.getDestination().getName()));
    }

    public void cancelRequest(UUID adminId, UUID requestId) {
        User admin = guard.admin(adminId);
        TravelRequest request = requests.findById(requestId).orElseThrow(() -> new NotFoundException("Request"));
        User student = request.getStudent().getUser();
        requestService.cancel(student.getId(), requestId);
        log.record(admin, student, "Cancelled a ride request",
                route(request.getPickup().getName(), request.getDestination().getName()));
    }

    /** Frees a person who is stuck in a ride. */
    public void clear(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        if (releaseEverything(user) == 0) {
            throw new BusinessException("There are no active rides to clear.");
        }
        log.record(admin, user, "Cleared active rides");
    }

    /** Takes someone out of every active ride, as a driver, host, passenger or someone waiting. */
    public int releaseEverything(User user) {
        UUID userId = user.getId();
        int released = 0;
        DriverProfile driver = drivers.findByUserId(userId).orElse(null);
        if (driver != null) {
            for (Trip trip : trips.findByDriverAndStatuses(driver.getId(), TripStatus.ACTIVE)) {
                end(userId, trip);
                released++;
            }
            driver.setAvailability(Availability.OFFLINE);
        }
        StudentProfile student = students.findByUserId(userId).orElse(null);
        if (student != null) {
            released += releaseStudent(userId, student);
        }
        return released;
    }

    private int releaseStudent(UUID userId, StudentProfile student) {
        int released = 0;
        // Requests first: cancelling one also frees the seat it is matched to.
        for (TravelRequest request : requests.findByStudentAndStatuses(student.getId(), RequestStatus.ACTIVE)) {
            if (request.getStatus() == RequestStatus.IN_TRIP) {
                request.markCompleted();
            } else {
                requestService.cancel(userId, request.getId());
            }
            released++;
        }
        for (CompanionRideMember member : joined(student)) {
            if (RideStatus.JOINABLE_OR_FULL.contains(member.getRide().getStatus())) {
                companions.leave(userId, member.getRide().getId());
            } else {
                member.leave();   // already on the road: the host still completes it for the others
            }
            released++;
        }
        for (CompanionRide ride : rides.findByHostAndStatuses(student.getId(), RideStatus.ACTIVE)) {
            end(userId, ride);
            released++;
        }
        return released;
    }

    private void end(UUID driverUserId, Trip trip) {
        if (trip.getStatus() == TripStatus.IN_PROGRESS) {
            tripService.complete(driverUserId, trip.getId());
        } else {
            tripService.cancel(driverUserId, trip.getId());
        }
    }

    private void end(UUID hostUserId, CompanionRide ride) {
        if (ride.getStatus() == RideStatus.STARTED) {
            companions.complete(hostUserId, ride.getId());
        } else {
            companions.cancel(hostUserId, ride.getId());
        }
    }

    private List<CompanionRideMember> joined(StudentProfile student) {
        return members.findActiveForStudent(student.getId(), MemberStatus.JOINED, RideStatus.ACTIVE);
    }

    private static LiveRides.Row row(Trip t) {
        User driver = t.getDriver().getUser();
        boolean started = t.getStatus() == TripStatus.IN_PROGRESS;
        return new LiveRides.Row(t.getId(), driver.getId(), driver.getFullName(), t.getPickup().getName(),
                t.getDestination().getName(), t.getSeatsReserved(), t.getStatus(), started,
                started ? t.getStartTime() : t.getCreatedAt());
    }

    private static LiveRides.Row row(CompanionRide r) {
        User host = r.getHost().getUser();
        return new LiveRides.Row(r.getId(), host.getId(), host.getFullName(), r.getPickup().getName(),
                r.getDestination().getName(), r.bookedSeats(), r.getStatus(), r.getStatus() == RideStatus.STARTED,
                r.getDepartureTime());
    }

    private static LiveRides.Row row(TravelRequest r) {
        User student = r.getStudent().getUser();
        return new LiveRides.Row(r.getId(), student.getId(), student.getFullName(), r.getPickup().getName(),
                r.getDestination().getName(), r.getSeatsRequired(), r.getStatus(),
                r.getStatus() == RequestStatus.IN_TRIP, r.getCreatedAt());
    }

    private static String route(String from, String to) {
        return from + " → " + to;
    }
}
