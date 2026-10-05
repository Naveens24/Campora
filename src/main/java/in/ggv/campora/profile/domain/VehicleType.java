package in.ggv.campora.profile.domain;

public enum VehicleType {
    AUTO("Auto rickshaw"),
    E_RICKSHAW("E-rickshaw"),
    CAR("Car / cab");

    private final String label;

    VehicleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
