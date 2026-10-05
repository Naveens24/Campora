package in.ggv.campora.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;

/**
 * One-time passwords for mobile verification.
 *
 * <ul>
 *   <li>Codes come from {@link SecureRandom} and are stored only as SHA-256 hashes.</li>
 *   <li>Each code expires (default 5 min) and allows a limited number of guesses.</li>
 *   <li>Resending is throttled to stop SMS flooding.</li>
 * </ul>
 * State is in memory, which suits a single instance; move it to Redis
 * when running more than one node.
 */
@Service
public class OtpService {

    private record Challenge(String hash, Instant expiresAt, int attemptsLeft, Instant issuedAt) {
    }

    /** {@code code} is returned only so dev mode can show it on screen. */
    public record IssuedOtp(String code, Instant expiresAt) {
    }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CamporaProperties.Otp config;
    private final SmsGateway sms;
    private final Clock clock;

    public OtpService(CamporaProperties properties, SmsGateway sms, Clock clock) {
        this.config = properties.otp();
        this.sms = sms;
        this.clock = clock;
    }

    public IssuedOtp issue(String mobile) {
        Instant now = clock.instant();
        Challenge existing = challenges.get(mobile);
        if (existing != null) {
            Instant nextAllowed = existing.issuedAt().plus(config.resendCooldown());
            if (nextAllowed.isAfter(now)) {
                long wait = Duration.between(now, nextAllowed).toSeconds() + 1;
                throw new BusinessException("Please wait " + wait + " s before requesting another code.");
            }
        }
        String code = generateCode();
        Instant expiresAt = now.plus(config.ttl());
        challenges.put(mobile, new Challenge(hash(mobile, code), expiresAt, config.maxAttempts(), now));
        sms.send(mobile, "Your Campora verification code is " + code
                + ". It expires in " + config.ttl().toMinutes() + " minutes.");
        return new IssuedOtp(code, expiresAt);
    }

    /** @throws BusinessException when the code is wrong, expired or out of attempts */
    public void verify(String mobile, String code) {
        Instant now = clock.instant();
        Challenge challenge = challenges.get(mobile);
        if (challenge == null || !challenge.expiresAt().isAfter(now)) {
            challenges.remove(mobile);
            throw new BusinessException("The code has expired. Tap “Resend code” to get a new one.");
        }
        String candidate = hash(mobile, code == null ? "" : code.trim());
        boolean matches = MessageDigest.isEqual(
                challenge.hash().getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int left = challenge.attemptsLeft() - 1;
            if (left <= 0) {
                challenges.remove(mobile);
                throw new BusinessException("Too many wrong attempts. Request a new code.");
            }
            challenges.put(mobile, new Challenge(challenge.hash(), challenge.expiresAt(), left, challenge.issuedAt()));
            throw new BusinessException("Incorrect code. " + left + " attempt(s) left.");
        }
        challenges.remove(mobile);
    }

    @Scheduled(fixedDelay = 300_000)
    void purgeExpired() {
        Instant now = clock.instant();
        challenges.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, config.length());
        return String.format("%0" + config.length() + "d", random.nextInt(bound));
    }

    private static String hash(String mobile, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((mobile + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
