package in.ggv.campora.admin.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.profile.domain.Availability;

public record DriverRow(UUID id, String name, String mobile, String vehicle, String vehicleNumber,
                        int capacity, String nativeArea, boolean verified, Availability availability,
                        Instant registeredAt) {
}
