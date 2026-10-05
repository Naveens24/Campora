package in.ggv.campora.mobility.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Travel-request lifecycle:
 * <pre>
 *   OPEN ──► MATCHED ──► IN_TRIP ──► COMPLETED
 *     │         │
 *     │         └──► OPEN (driver cancelled / companion left)
 *     ├──► CANCELLED (by student)
 *     └──► EXPIRED   (no ride within the TTL)
 * </pre>
 */
public enum RequestStatus {
    OPEN,
    MATCHED,
    IN_TRIP,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public static final Set<RequestStatus> ACTIVE = EnumSet.of(OPEN, MATCHED, IN_TRIP);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
