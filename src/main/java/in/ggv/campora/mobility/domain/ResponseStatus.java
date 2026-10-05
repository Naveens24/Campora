package in.ggv.campora.mobility.domain;

public enum ResponseStatus {
    /** The driver holds these seats for the request. At most one per request. */
    ACCEPTED,
    /** Released — by the driver, or because the student cancelled. */
    CANCELLED
}
