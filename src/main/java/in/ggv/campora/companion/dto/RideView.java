package in.ggv.campora.companion.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ggv.campora.companion.domain.RideStatus;
import in.ggv.campora.trip.dto.PassengerView;

/**
 * A companion ride as displayed to a student. {@code hostMobile} and
 * {@code members} are filled only for the host and for joined riders.
 */
public record RideView(
        UUID id,
        String hostName,
        String hostMobile,
        String pickup,
        String destination,
        Instant departureTime,
        int totalSeats,
        int availableSeats,
        RideStatus status,
        String note,
        boolean matchesMyRoute,
        List<PassengerView> members) {

    public int bookedSeats() {
        return totalSeats - availableSeats;
    }

    public boolean notStarted() {
        return status == RideStatus.OPEN || status == RideStatus.FULL;
    }

    public boolean started() {
        return status == RideStatus.STARTED;
    }
}
