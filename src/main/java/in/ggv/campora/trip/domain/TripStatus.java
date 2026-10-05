package in.ggv.campora.trip.domain;

import java.util.EnumSet;
import java.util.Set;

/** SCHEDULED ──► IN_PROGRESS ──► COMPLETED, or SCHEDULED ──► CANCELLED. */
public enum TripStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static final Set<TripStatus> ACTIVE = EnumSet.of(SCHEDULED, IN_PROGRESS);
}
