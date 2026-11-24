package in.ggv.campora.web.form;

import java.util.UUID;

/** Pickup and drop stop on the public pages; both optional. */
public class RouteForm {

    private UUID from;

    private UUID to;

    public UUID getFrom() {
        return from;
    }

    public void setFrom(UUID from) {
        this.from = from;
    }

    public UUID getTo() {
        return to;
    }

    public void setTo(UUID to) {
        this.to = to;
    }

    public boolean complete() {
        return from != null && to != null;
    }
}
