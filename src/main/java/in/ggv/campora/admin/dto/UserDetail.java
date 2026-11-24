package in.ggv.campora.admin.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.profile.domain.VehicleType;

/**
 * Everything the admin panel shows about one account.
 *
 * @param student    null unless a student profile exists
 * @param driver     null unless a driver profile exists
 * @param manageable whether the signed-in admin may change access, status and role
 * @param canGrantAdmin whether the signed-in admin is an owner
 * @param self       whether this is the signed-in admin's own account
 */
public record UserDetail(UUID id, String firstName, String lastName, String fullName, String email, String mobile,
                         RoleName role, UserStatus status, boolean verified, boolean emailVerified,
                         boolean googleLinked, boolean hasPassword, boolean temporaryPassword, boolean owner,
                         Instant joinedAt, Instant lastLoginAt, Student student, Driver driver,
                         boolean manageable, boolean self, boolean canGrantAdmin) {

    public record Student(UUID departmentId, String department, int semester) {
    }

    public record Driver(VehicleType vehicleType, String vehicleNumber, int seats, String area,
                         boolean blocked, boolean online) {
    }

    public boolean isStudent() {
        return role == RoleName.STUDENT;
    }

    public boolean isDriver() {
        return role == RoleName.DRIVER;
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    public boolean suspended() {
        return status == UserStatus.SUSPENDED;
    }

    public boolean editable() {
        return manageable || self;
    }
}
