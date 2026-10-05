package in.ggv.campora.identity.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.UserRepository;

/** Registration and mobile verification. */
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

    /** Creates an unverified account and returns its normalised mobile number. */
    public String register(String firstName, String lastName, String rawMobile, String rawPassword) {
        String mobile = MobileNumbers.normalize(rawMobile);
        if (users.existsByMobileNumber(mobile)) {
            throw new BusinessException("An account with this mobile number already exists. Please sign in.");
        }
        users.save(new User(firstName.trim(), lastName.trim(), mobile, passwordEncoder.encode(rawPassword)));
        return mobile;
    }

    @Transactional(readOnly = true)
    public OtpService.IssuedOtp sendVerificationCode(UUID userId) {
        User user = require(userId);
        if (user.isMobileVerified()) {
            throw new BusinessException("Your mobile number is already verified.");
        }
        return otpService.issue(user.getMobileNumber());
    }

    public void verifyMobile(UUID userId, String code) {
        User user = require(userId);
        if (user.isMobileVerified()) {
            return;
        }
        otpService.verify(user.getMobileNumber(), code);
        user.markMobileVerified(clock.instant());
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }
}
