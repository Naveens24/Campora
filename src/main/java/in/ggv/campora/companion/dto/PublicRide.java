package in.ggv.campora.companion.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.companion.domain.CompanionRide;

/** A companion ride as anyone may see it: no host name, number or note. */
public record PublicRide(UUID id, UUID pickupId, String pickup, UUID destinationId, String destination,
                         Instant departureTime, int availableSeats) {

    public static PublicRide of(CompanionRide ride) {
        return new PublicRide(ride.getId(), ride.getPickup().getId(), ride.getPickup().getName(),
                ride.getDestination().getId(), ride.getDestination().getName(),
                ride.getDepartureTime(), ride.getAvailableSeats());
    }

    public boolean onRoute(UUID from, UUID to) {
        return pickupId.equals(from) && destinationId.equals(to);
    }

    /** Orders near misses: same drop first, then same pickup, then the rest. */
    public int distanceFrom(UUID from, UUID to) {
        return destinationId.equals(to) ? 0 : pickupId.equals(from) ? 1 : 2;
    }
}
