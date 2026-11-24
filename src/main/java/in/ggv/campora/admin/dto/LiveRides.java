package in.ggv.campora.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything moving or waiting right now. */
public record LiveRides(List<Row> trips, List<Row> companions, List<Row> waiting) {

    /**
     * @param started whether the ride is already under way
     * @param since   when it started, leaves or was requested
     */
    public record Row(UUID id, UUID userId, String person, String pickup, String destination, int seats,
                      Enum<?> status, boolean started, Instant since) {
    }
}
