package in.ggv.campora.web;

import java.io.Serializable;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

/**
 * The ride a visitor picked before signing in. It waits in the session through
 * sign-in or sign-up, then opens the right page so nothing is picked twice.
 * It never books anything by itself: the student still confirms.
 */
public record RideIntent(Kind kind, UUID pickupId, UUID destinationId, UUID rideId, String from, String to)
        implements Serializable {

    /** Also read by the {@code pendingRide} template fragment. */
    static final String KEY = "rideIntent";

    public enum Kind {
        JOIN("Join this ride"),
        REQUEST("Request this ride"),
        OFFER("Offer your ride");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean hasRoute() {
        return from != null;
    }

    void keep(HttpSession session) {
        session.setAttribute(KEY, this);
    }

    /** The student page that finishes what the visitor started. */
    String path() {
        String route = pickupId == null ? "" : "?from=" + pickupId + "&to=" + destinationId;
        return switch (kind) {
            case JOIN -> "/companion?ride=" + rideId;
            case REQUEST -> "/student" + route;
            case OFFER -> "/companion" + route + "#panel-offer";
        };
    }

    /** Where a student goes after signing in: the ride they picked, or their usual page. Used once. */
    public static String studentHome(HttpSession session) {
        Object intent = session.getAttribute(KEY);
        session.removeAttribute(KEY);
        return intent instanceof RideIntent ride ? ride.path() : "/student";
    }
}
