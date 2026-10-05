package in.ggv.campora.profile.dto;

public record DriverStatusView(String name, String vehicle, String vehicleNumber, int capacity,
                               boolean verified, boolean online) {
}
