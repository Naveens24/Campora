package in.ggv.campora.trip.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.companion.domain.MemberStatus;
import in.ggv.campora.companion.repository.CompanionRideMemberRepository;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.mobility.domain.ResponseStatus;
import in.ggv.campora.mobility.repository.DriverResponseRepository;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.dto.JourneyView;

/** A student's unified travel history across auto and companion rides. */
@Service
@Transactional(readOnly = true)
public class JourneyService {

    private final DriverResponseRepository responses;
    private final CompanionRideMemberRepository members;
    private final CompanionRideRepository rides;
    private final ProfileService profiles;

    public JourneyService(DriverResponseRepository responses, CompanionRideMemberRepository members,
                          CompanionRideRepository rides, ProfileService profiles) {
        this.responses = responses;
        this.members = members;
        this.rides = rides;
        this.profiles = profiles;
    }

    public List<JourneyView> studentJourneys(UUID userId, int limit) {
        StudentProfile student = profiles.requireStudent(userId);
        PageRequest page = PageRequest.of(0, limit);
        List<JourneyView> all = new ArrayList<>();

        responses.findStudentRides(student.getId(), ResponseStatus.ACCEPTED, page).forEach(r -> {
            Trip t = r.getTrip();
            all.add(new JourneyView("AUTO", t.getPickup().getName(), t.getDestination().getName(),
                    r.getDriver().getUser().getFullName() + " · " + r.getDriver().getVehicleNumber(),
                    r.getSeatsReserved(), t.getStatus().name(),
                    t.getStartTime() != null ? t.getStartTime() : t.getCreatedAt()));
        });

        members.findStudentHistory(student.getId(), MemberStatus.JOINED, page).forEach(m -> {
            CompanionRide ride = m.getRide();
            all.add(new JourneyView("COMPANION", ride.getPickup().getName(), ride.getDestination().getName(),
                    ride.getHost().getUser().getFullName(), m.getSeats(), ride.getStatus().name(),
                    ride.getDepartureTime()));
        });

        rides.findHostHistory(student.getId(), page).forEach(ride ->
                all.add(new JourneyView("HOSTED", ride.getPickup().getName(), ride.getDestination().getName(),
                        "You hosted · " + ride.bookedSeats() + " seat(s) filled", ride.getTotalSeats(),
                        ride.getStatus().name(), ride.getDepartureTime())));

        return all.stream()
                .sorted(Comparator.comparing(JourneyView::when).reversed())
                .limit(limit)
                .toList();
    }
}
