package in.ggv.campora.identity.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.UserRepository;

/** Registration, email verification, Google sign-in and contact details. */
@Service
@Transactional
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, OtpService otpService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.clock = clock;
    }

    /** Creates an unverified account. */
    public UUID register(String firstName, String lastName, String rawEmail, String rawMobile, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        String mobile = MobileNumbers.normalize(rawMobile);
        claim(users.findByEmail(email), null, "An account with this email already exists. Please sign in.");
        claimMobile(mobile, null);
        return users.save(new User(firstName.trim(), lastName.trim(), email, mobile,
                passwordEncoder.encode(rawPassword))).getId();
    }

    /** Sets or corrects the address of an account that is not verified yet. */
    public void changePendingEmail(UUID userId, String rawEmail) {
        User user = require(userId);
        if (user.isVerified()) {
            throw new BusinessException("Your account is already verified.");
        }
        String email = normalizeEmail(rawEmail);
        claim(users.findByEmail(email), userId, "An account with this email already exists. Please sign in.");
        user.setPendingEmail(email);
    }

    /** No transaction: sending mail must not hold a database connection. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OtpService.IssuedOtp sendVerificationCode(UUID userId) {
        User user = require(userId);
        if (user.isVerified()) {
            throw new BusinessException("Your account is already verified.");
        }
        if (user.getEmail() == null) {
            throw new BusinessException("Add your email address first.");
        }
        return otpService.issue(OtpPurpose.VERIFY_EMAIL, user.getEmail());
    }

    public void verifyEmail(UUID userId, String code) {
        User user = require(userId);
        if (user.isVerified()) {
            return;
        }
        if (user.getEmail() == null) {
            throw new BusinessException("Add your email address first.");
        }
        otpService.verify(OtpPurpose.VERIFY_EMAIL, user.getEmail(), code);
        user.markEmailVerified(clock.instant());
    }

    /**
     * Emails a reset code, or does nothing when no usable account has proven this address.
     * No transaction: sending mail must not hold a database connection.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<OtpService.IssuedOtp> sendPasswordReset(String rawEmail) {
        return resettable(normalizeEmail(rawEmail))
                .map(user -> otpService.issue(OtpPurpose.RESET_PASSWORD, user.getEmail()));
    }

    public UUID resetPassword(String rawEmail, String code, String newPassword) {
        User user = resettable(normalizeEmail(rawEmail))
                .orElseThrow(() -> new BusinessException("The code has expired. Please request a new one."));
        otpService.verify(OtpPurpose.RESET_PASSWORD, user.getEmail(), code);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return user.getId();
    }

    private Optional<User> resettable(String email) {
        return users.findByEmail(email)
                .filter(user -> user.isEmailVerified() && user.getStatus() != UserStatus.SUSPENDED);
    }

    /** Finds, links or creates the account for a Google identity whose email Google has verified. */
    public UUID signInWithGoogle(String subject, String rawEmail, String firstName, String lastName) {
        Optional<User> known = users.findByGoogleSubject(subject);
        if (known.isPresent()) {
            return known.get().getId();
        }
        String email = normalizeEmail(rawEmail);
        Optional<User> sameEmail = users.findByEmail(email);
        if (sameEmail.isPresent() && (sameEmail.get().isEmailVerified()
                || sameEmail.get().getStatus() == UserStatus.SUSPENDED)) {
            User user = sameEmail.get();
            user.linkGoogle(subject);
            return user.getId();
        }
        // An unproven registration of this address may be someone else's:
        // discard it (and its password) rather than link to it.
        claim(sameEmail, null, "An account with this email already exists. Please sign in.");
        User user = new User(clip(firstName, "Campora"), clip(lastName, ""), email, null, null);
        user.linkGoogle(subject);
        user.markEmailVerified(clock.instant());
        return users.save(user).getId();
    }

    /** Google accounts arrive without a phone number, which ride partners need. */
    public void setMobile(UUID userId, String rawMobile) {
        User user = require(userId);
        if (!user.isVerified()) {
            throw new BusinessException("Verify your email first.");
        }
        if (user.getMobileNumber() != null) {
            return;
        }
        String mobile = MobileNumbers.normalize(rawMobile);
        claimMobile(mobile, userId);
        user.setMobileNumber(mobile);
    }

    private void claimMobile(String mobile, UUID claimant) {
        claim(users.findByMobileNumber(mobile), claimant,
                "An account with this mobile number already exists. Please sign in to that account.");
    }

    /** Unverified registrations (no role, no data) give way; verified accounts never do. */
    private void claim(Optional<User> holder, UUID claimant, String taken) {
        if (holder.isEmpty() || holder.get().getId().equals(claimant)) {
            return;
        }
        User other = holder.get();
        if (other.getStatus() != UserStatus.PENDING_VERIFICATION || other.isVerified() || other.getRole() != null) {
            throw new BusinessException(taken);
        }
        users.delete(other);
        users.flush();
    }

    private static String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BusinessException("Enter a valid email address.");
        }
        return email;
    }

    private static String clip(String name, String fallback) {
        String value = name == null || name.isBlank() ? fallback : name.trim();
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }
}
