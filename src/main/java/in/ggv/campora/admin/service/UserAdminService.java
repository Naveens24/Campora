package in.ggv.campora.admin.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.admin.dto.UserDetail;
import in.ggv.campora.admin.dto.UserRow;
import in.ggv.campora.admin.dto.UserView;
import in.ggv.campora.campus.domain.Department;
import in.ggv.campora.campus.repository.DepartmentRepository;
import in.ggv.campora.campus.repository.DestinationRequestRepository;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.companion.repository.CompanionRideRepository;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.RoleRepository;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.identity.service.MobileNumbers;
import in.ggv.campora.identity.service.OwnerAccounts;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/**
 * Account support: find people, fix their details, help them back in, pause or
 * delete accounts. Every change is logged. Callers sign the person out where a
 * change must take effect at once.
 */
@Service
@Transactional
public class UserAdminService {

    public static final int PAGE_SIZE = 20;

    /** No look-alikes (0/o, 1/l/i), so it can be read out over the phone. */
    private static final String READABLE = "abcdefghjkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final RoleRepository roles;
    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;
    private final DepartmentRepository departments;
    private final CompanionRideRepository rides;
    private final DestinationRequestRepository suggestions;
    private final AccountService accounts;
    private final OwnerAccounts owners;
    private final RideAdminService rideAdmin;
    private final AdminGuard guard;
    private final AdminLog log;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserAdminService(UserRepository users, RoleRepository roles, StudentProfileRepository students,
                            DriverProfileRepository drivers, DepartmentRepository departments,
                            CompanionRideRepository rides, DestinationRequestRepository suggestions,
                            AccountService accounts, OwnerAccounts owners, RideAdminService rideAdmin,
                            AdminGuard guard, AdminLog log, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.students = students;
        this.drivers = drivers;
        this.departments = departments;
        this.rides = rides;
        this.suggestions = suggestions;
        this.accounts = accounts;
        this.owners = owners;
        this.rideAdmin = rideAdmin;
        this.guard = guard;
        this.log = log;
        this.encoder = encoder;
        this.clock = clock;
    }

    // ── read side ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserRow> search(UserView view, String query, int page) {
        Page<User> found = users.findAll(UserSearch.matching(view, query),
                PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, UserRow> rows = rows(found.getContent());
        return found.map(user -> rows.get(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<UserRow> newest() {
        List<User> newest = users.findTop6ByStatusNotOrderByCreatedAtDesc(UserStatus.DELETED);
        Map<UUID, UserRow> rows = rows(newest);
        return newest.stream().map(user -> rows.get(user.getId())).toList();
    }

    @Transactional(readOnly = true)
    public UserDetail detail(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User u = guard.user(userId);
        UserDetail.Student student = students.findDetailedByUserId(userId)
                .map(s -> new UserDetail.Student(s.getDepartment().getId(), s.getDepartment().getName(), s.getSemester()))
                .orElse(null);
        UserDetail.Driver driver = drivers.findByUserId(userId)
                .map(d -> new UserDetail.Driver(d.getVehicleType(), d.getVehicleNumber(), d.getSeatCapacity(),
                        d.getNativeArea(), !d.isVerified(), d.isOnline()))
                .orElse(null);
        return new UserDetail(u.getId(), u.getFirstName(), u.getLastName(), u.getFullName(), u.getEmail(),
                u.getMobileNumber(), roleOf(u), u.getStatus(), u.isVerified(), u.isEmailVerified(),
                u.isGoogleLinked(), u.getPasswordHash() != null, u.isPasswordChangeRequired(), owners.isOwner(u),
                u.getCreatedAt(), u.getLastLoginAt(), student, driver, guard.canManage(admin, u), admin.equals(u),
                owners.isOwner(admin));
    }

    // ── details ────────────────────────────────────────────────

    public void updateAccount(UUID adminId, UUID userId, String firstName, String lastName, String rawEmail,
                              String rawMobile) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireEdit(admin, user);

        List<String> changed = new ArrayList<>();
        String first = firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (!first.equals(user.getFirstName()) || !last.equals(user.getLastName())) {
            user.rename(first, last);
            changed.add("name");
        }
        if (changeEmail(admin, user, rawEmail)) {
            changed.add("email");
        }
        if (changeMobile(user, rawMobile)) {
            changed.add("mobile number");
        }
        if (!changed.isEmpty()) {
            log.record(admin, user, "Changed " + String.join(", ", changed));
        }
    }

    /** @return true when the profile is new, so the person must be signed out to pick it up */
    public boolean saveStudent(UUID adminId, UUID userId, UUID departmentId, int semester) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        requireRole(user, RoleName.STUDENT);
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department"));
        StudentProfile profile = students.findByUserId(userId).orElse(null);
        if (profile == null) {
            students.save(new StudentProfile(user, department, semester));
        } else {
            profile.update(department, semester);
        }
        log.record(admin, user, "Updated student details");
        return profile == null;
    }

    /** @return true when the profile is new, so the person must be signed out to pick it up */
    public boolean saveDriver(UUID adminId, UUID userId, VehicleType type, String vehicleNumber, int seats,
                              String area) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        requireRole(user, RoleName.DRIVER);
        String plate = vehicleNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String home = area == null || area.isBlank() ? null : area.trim();
        DriverProfile profile = drivers.findByUserId(userId).orElse(null);
        drivers.findByVehicleNumberIgnoreCase(plate).filter(other -> !other.equals(profile)).ifPresent(other -> {
            throw new BusinessException("Vehicle " + plate + " is already registered to another driver.");
        });
        if (profile == null) {
            drivers.save(new DriverProfile(user, type, plate, seats, home));
        } else {
            profile.update(type, plate, seats, home);
        }
        log.record(admin, user, "Updated driver details");
        return profile == null;
    }

    // ── access ─────────────────────────────────────────────────

    /** @return the password to pass on; it must be replaced at the next sign-in */
    public String setTemporaryPassword(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        if (user.getEmail() == null && user.getMobileNumber() == null) {
            throw new BusinessException("Add an email or mobile number first, so they can sign in.");
        }
        String password = temporaryPassword();
        user.setTemporaryPasswordHash(encoder.encode(password));
        log.record(admin, user, "Set a temporary password");
        return password;
    }

    public void markVerified(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        if (user.isVerified()) {
            throw new BusinessException("This account is already verified.");
        }
        if (user.getEmail() != null) {
            user.markEmailVerified(clock.instant());
        } else {
            user.markMobileVerified(clock.instant());
        }
        log.record(admin, user, "Marked the account as verified");
    }

    public void disconnectGoogle(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        if (user.getPasswordHash() == null) {
            throw new BusinessException("They only sign in with Google. Set a temporary password first.");
        }
        user.unlinkGoogle();
        log.record(admin, user, "Disconnected Google sign-in");
    }

    public void logSignOut(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        log.record(admin, user, "Signed out on all devices");
    }

    public void suspend(UUID adminId, UUID userId, String reason) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("This account is already suspended.");
        }
        rideAdmin.releaseEverything(user);
        user.suspend();
        log.record(admin, user, "Suspended the account", reason);
    }

    public void reactivate(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        user.reactivate();
        log.record(admin, user, "Reactivated the account");
    }

    public void setDrivingBlocked(UUID adminId, UUID userId, boolean blocked) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        DriverProfile driver = drivers.findByUserId(userId).orElseThrow(() -> new NotFoundException("Driver"));
        if (blocked) {
            driver.revokeVerification();
        } else {
            driver.verify();
        }
        log.record(admin, user, blocked ? "Blocked from driving" : "Allowed to drive again");
    }

    /** Switching between student and driver keeps the old profile, so switching back restores it. */
    public void changeRole(UUID adminId, UUID userId, RoleName role) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        if (role == roleOf(user)) {
            throw new BusinessException("They already have this role.");
        }
        if (role == RoleName.ADMIN) {
            guard.requireOwner(admin);
            if (!user.isVerified()) {
                throw new BusinessException("Only verified accounts can become admins.");
            }
        }
        rideAdmin.releaseEverything(user);
        user.assignRole(roles.findByName(role).orElseThrow(() -> new NotFoundException("Role")));
        log.record(admin, user, "Changed role to " + role.name().toLowerCase(Locale.ROOT));
    }

    // ── open and close accounts ────────────────────────────────

    public record NewAccount(UUID userId, String temporaryPassword) {
    }

    public NewAccount create(UUID adminId, String firstName, String lastName, String rawEmail, String rawMobile,
                             RoleName role) {
        User admin = guard.admin(adminId);
        if (role != RoleName.STUDENT && role != RoleName.DRIVER) {
            throw new BusinessException("Choose Student or Driver.");
        }
        Instant now = clock.instant();
        User user = new User(firstName.trim(), lastName == null ? "" : lastName.trim(), null, null, null);
        if (isBlank(rawMobile)) {
            throw new BusinessException("Mobile number is required.");
        }
        changeMobile(user, rawMobile);
        user.markMobileVerified(now);
        changeEmail(admin, user, rawEmail);
        String password = temporaryPassword();
        user.setTemporaryPasswordHash(encoder.encode(password));
        user.assignRole(roles.findByName(role).orElseThrow(() -> new NotFoundException("Role")));
        users.save(user);
        log.record(admin, user, "Created the account");
        return new NewAccount(user.getId(), password);
    }

    /** Erases the person, keeps the anonymous ride history. */
    public void delete(UUID adminId, UUID userId) {
        User admin = guard.admin(adminId);
        User user = guard.user(userId);
        guard.requireManage(admin, user);
        rideAdmin.releaseEverything(user);
        students.findByUserId(userId).ifPresent(student -> {
            rides.clearNotes(student.getId());
            suggestions.clearReasons(student.getId());
            users.clearEnrollmentNumber(userId);
        });
        drivers.findByUserId(userId).ifPresent(DriverProfile::erase);
        user.erase();
        log.forget(userId);
        log.record(admin, null, "Deleted an account");
    }

    // ── internals ──────────────────────────────────────────────

    /** An address entered by an admin counts as confirmed, unless the account is still unverified. */
    private boolean changeEmail(User admin, User user, String rawEmail) {
        if (isBlank(rawEmail)) {
            if (user.getEmail() == null) {
                return false;
            }
            throw new BusinessException("Email can't be left empty for this account.");
        }
        String email = AccountService.normalizeEmail(rawEmail);
        if (email.equals(user.getEmail())) {
            return false;
        }
        if (owners.isOwnerEmail(email) && !owners.isOwner(admin) || owners.isOwner(user)) {
            throw new BusinessException("The owner's email can only be changed on the server.");
        }
        claim(() -> accounts.claimEmail(email, user.getId()), "Another account already uses this email.");
        if (user.isVerified()) {
            user.setConfirmedEmail(email, clock.instant());
        } else {
            user.setPendingEmail(email);
        }
        return true;
    }

    private boolean changeMobile(User user, String rawMobile) {
        if (isBlank(rawMobile)) {
            if (user.getMobileNumber() == null) {
                return false;
            }
            throw new BusinessException("Mobile number can't be left empty for this account.");
        }
        String mobile = MobileNumbers.normalize(rawMobile);
        if (mobile.equals(user.getMobileNumber())) {
            return false;
        }
        claim(() -> accounts.claimMobile(mobile, user.getId()), "Another account already uses this mobile number.");
        user.setMobileNumber(mobile);
        return true;
    }

    private static void claim(Runnable attempt, String taken) {
        try {
            attempt.run();
        } catch (BusinessException e) {
            throw new BusinessException(taken);
        }
    }

    private void requireRole(User user, RoleName role) {
        if (roleOf(user) != role) {
            throw new BusinessException("This account is not a " + role.name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private Map<UUID, UserRow> rows(List<User> page) {
        List<UUID> ids = page.stream().map(User::getId).toList();
        Map<UUID, StudentProfile> studentOf = students.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
        Map<UUID, DriverProfile> driverOf = drivers.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.getUser().getId(), Function.identity()));
        return page.stream().map(u -> row(u, studentOf.get(u.getId()), driverOf.get(u.getId())))
                .collect(Collectors.toMap(UserRow::id, Function.identity()));
    }

    private UserRow row(User u, StudentProfile student, DriverProfile driver) {
        RoleName role = roleOf(u);
        String about = null;
        if (role == RoleName.STUDENT && student != null) {
            about = student.getDepartment().getName() + " · Sem " + student.getSemester();
        } else if (role == RoleName.DRIVER && driver != null) {
            about = driver.getVehicleType().getLabel() + " · " + driver.getVehicleNumber();
        }
        return new UserRow(u.getId(), u.getFullName(), u.getEmail(), u.getMobileNumber(), role, u.getStatus(), about,
                role == RoleName.DRIVER && driver != null && !driver.isVerified(), owners.isOwner(u),
                u.getCreatedAt(), u.getLastLoginAt());
    }

    private String temporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                password.append('-');
            }
            password.append(READABLE.charAt(random.nextInt(READABLE.length())));
        }
        return password.toString();
    }

    private static RoleName roleOf(User user) {
        return user.getRole() == null ? null : user.getRole().getName();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
