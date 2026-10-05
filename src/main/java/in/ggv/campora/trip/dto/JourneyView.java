package in.ggv.campora.trip.dto;

import java.time.Instant;

/**
 * One line of a student's travel history, whatever the mode.
 *
 * @param kind        AUTO, COMPANION (rode along) or HOSTED (offered the ride)
 * @param counterpart driver / host name, or a summary for hosted rides
 */
public record JourneyView(String kind, String pickup, String destination, String counterpart,
                          int seats, String status, Instant when) {
}
