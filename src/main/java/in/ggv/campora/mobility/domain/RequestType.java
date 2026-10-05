package in.ggv.campora.mobility.domain;

/** Which kind of ride the student will accept. */
public enum RequestType {
    /** Auto / e-rickshaw via the driver hotspot board. */
    AUTO("Auto only"),
    /** A fellow student offering a companion ride. */
    COMPANION("Companion only"),
    /** Whichever comes first. */
    ANY("Any ride");

    private final String label;

    RequestType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean acceptsAuto() {
        return this != COMPANION;
    }

    public boolean acceptsCompanion() {
        return this != AUTO;
    }
}
