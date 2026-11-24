package in.ggv.campora.admin.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.UserStatus;

/** @param about department or vehicle, whichever fits the role; may be null */
public record UserRow(UUID id, String name, String email, String mobile, RoleName role, UserStatus status,
                      String about, boolean drivingBlocked, boolean owner, Instant joinedAt, Instant lastLoginAt) {

    public String contact() {
        if (email == null) {
            return mobile == null ? "No contact details" : mobile;
        }
        return mobile == null ? email : mobile + " · " + email;
    }
}
