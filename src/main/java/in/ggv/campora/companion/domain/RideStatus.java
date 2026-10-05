package in.ggv.campora.companion.domain;

import java.util.EnumSet;
import java.util.Set;

/** OPEN ⇄ FULL ──► STARTED ──► COMPLETED, or OPEN/FULL ──► CANCELLED. */
public enum RideStatus {
    OPEN,
    FULL,
    STARTED,
    COMPLETED,
    CANCELLED;

    public static final Set<RideStatus> ACTIVE = EnumSet.of(OPEN, FULL, STARTED);
    public static final Set<RideStatus> JOINABLE_OR_FULL = EnumSet.of(OPEN, FULL);
}
