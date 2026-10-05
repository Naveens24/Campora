package in.ggv.campora.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ggv.campora.trip.domain.TripStatus;

public record DriverTripView(
        UUID id,
        String pickup,
        String destination,
        int seats,
        TripStatus status,
        Instant createdAt,
        Instant startTime,
        Instant endTime,
        List<PassengerView> passengers) {

    public boolean scheduled() {
        return status == TripStatus.SCHEDULED;
    }

    public boolean inProgress() {
        return status == TripStatus.IN_PROGRESS;
    }
}
