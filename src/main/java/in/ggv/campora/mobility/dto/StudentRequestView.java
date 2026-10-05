package in.ggv.campora.mobility.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;

/** The student's live request, as shown on the student dashboard. */
public record StudentRequestView(
        UUID id,
        String pickup,
        String destination,
        RequestType type,
        int seats,
        RequestStatus status,
        Instant createdAt,
        Instant expiresAt,
        long othersOnRoute,
        MatchInfo match) {

    public boolean cancellable() {
        return status == RequestStatus.OPEN || status == RequestStatus.MATCHED;
    }

    /** Position on the four-station progress line (1 = open … 4 = done). */
    public int stage() {
        return switch (status) {
            case OPEN -> 1;
            case MATCHED -> 2;
            case IN_TRIP -> 3;
            default -> 4;
        };
    }
}
