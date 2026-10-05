package in.ggv.campora.identity.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.campus.domain.Department;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.Role;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.RoleRepository;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/**
 * After OTP verification: select a role, then complete the matching profile.
 */
@Service
@Transactional
public class OnboardingService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;
    private final CampusService campus;

    public OnboardingService(UserRepository users, RoleRepository roles, StudentProfileRepository students,
                             DriverProfileRepository drivers, CampusService campus) {
        this.users = users;
        this.roles = roles;
        this.students = students;
        this.drivers = drivers;
        this.campus = campus;
    }

    public void chooseRole(UUID userId, RoleName roleName) {
        if (roleName != RoleName.STUDENT && roleName != RoleName.DRIVER) {
            throw new BusinessException("Please choose Student or Driver.");
        }
        User user = requireUser(userId);
        if (!user.isMobileVerified()) {
            throw new BusinessException("Verify your mobile number first.");
        }
        if (hasProfile(user)) {
            throw new BusinessException("Your role is already set up.");
        }
        Role role = roles.findByName(roleName).orElseThrow(() -> new NotFoundException("Role"));
        user.assignRole(role);
    }

    public void createStudentProfile(UUID userId, UUID departmentId, String enrollmentNo, int semester) {
        User user = requireUserWithRole(userId, RoleName.STUDENT);
        if (students.existsByUserId(userId)) {
            throw new BusinessException("Your student profile already exists.");
        }
        String enrollment = enrollmentNo.trim().toUpperCase(Locale.ROOT);
        if (students.existsByEnrollmentNoIgnoreCase(enrollment)) {
            throw new BusinessException("Enrollment number " + enrollment + " is already registered.");
        }
        Department department = campus.requireActiveDepartment(departmentId);
        students.save(new StudentProfile(user, department, enrollment, semester));
    }

    public void createDriverProfile(UUID userId, VehicleType vehicleType, String vehicleNumber,
                                    int seatCapacity, String nativeArea) {
        User user = requireUserWithRole(userId, RoleName.DRIVER);
        if (drivers.existsByUserId(userId)) {
            throw new BusinessException("Your driver profile already exists.");
        }
        String plate = vehicleNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (drivers.existsByVehicleNumberIgnoreCase(plate)) {
            throw new BusinessException("Vehicle " + plate + " is already registered.");
        }
        String area = nativeArea == null || nativeArea.isBlank() ? null : nativeArea.trim();
        drivers.save(new DriverProfile(user, vehicleType, plate, seatCapacity, area));
    }

    private boolean hasProfile(User user) {
        return students.existsByUserId(user.getId()) || drivers.existsByUserId(user.getId());
    }

    private User requireUserWithRole(UUID userId, RoleName expected) {
        User user = users.findByIdWithRole(userId).orElseThrow(() -> new NotFoundException("User"));
        if (user.getRole() == null || user.getRole().getName() != expected) {
            throw new BusinessException("This profile does not match your selected role.");
        }
        return user;
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }
}
