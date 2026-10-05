package in.ggv.campora.mobility.dto;

import java.util.UUID;

/**
 * Outcome of a driver reserving seats on a hotspot.
 *
 * @param seatsFilled        seats actually allocated to this driver
 * @param requestsMatched    how many student requests they now serve
 * @param seatsStillWaiting  demand left on the route (0 = hotspot closed)
 */
public record ReservationResult(UUID tripId, int seatsFilled, int requestsMatched, long seatsStillWaiting) {

    public boolean hotspotClosed() {
        return seatsStillWaiting == 0;
    }
}
