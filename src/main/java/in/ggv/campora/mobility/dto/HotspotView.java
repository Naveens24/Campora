package in.ggv.campora.mobility.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One live demand cluster: every OPEN request sharing a (pickup, destination)
 * route. Drivers see only these aggregates — never who the students are.
 */
public record HotspotView(
        UUID pickupId,
        String pickupName,
        UUID destinationId,
        String destinationName,
        Long requests,
        Long seatsDemanded,
        Instant waitingSince) {

    /** Seats a driver with the given capacity can usefully offer here. */
    public int suggestedSeats(int capacity) {
        return (int) Math.min(capacity, seatsDemanded);
    }
}
