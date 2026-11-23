package in.ggv.campora.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;

/**
 * One-time passwords for email verification and password reset.
 *
 * <ul>
 *   <li>Codes come from {@link SecureRandom} and are stored only as SHA-256 hashes.</li>
 *   <li>Each code expires (default 5 min) and allows a limited number of guesses.</li>
 *   <li>Resending is throttled, and capped per hour so that asking for a fresh code
 *       cannot be used to buy unlimited guesses or flood a mailbox.</li>
 * </ul>
 * State is in memory, which suits a single instance; move it to Redis
 * when running more than one node. All state changes happen under this
 * object's lock; mail is sent outside it.
 */
@Service
public class OtpService {

    static final int MAX_CODES_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private record Challenge(String hash, Instant expiresAt, int attemptsLeft, Instant issuedAt) {
    }

    /** {@code code} is returned only so dev mode can show it on screen. */
    public record IssuedOtp(String code, Instant expiresAt) {
    }

    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Map<String, Deque<Instant>> issued = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CamporaProperties.Otp config;
    private final OtpSender sender;
    private final Clock clock;

    public OtpService(CamporaProperties properties, OtpSender sender, Clock clock) {
        this.config = properties.otp();
        this.sender = sender;
        this.clock = clock;
    }

    public IssuedOtp issue(OtpPurpose purpose, String email) {
        String key = key(purpose, email);
        String code = generateCode();
        Instant now = clock.instant();
        Challenge challenge = new Challenge(hash(key, code), now.plus(config.ttl()), config.maxAttempts(), now);
        synchronized (this) {
            Challenge existing = challenges.get(key);
            if (existing != null) {
                Instant nextAllowed = existing.issuedAt().plus(config.resendCooldown());
                if (nextAllowed.isAfter(now)) {
                    long wait = Duration.between(now, nextAllowed).toSeconds() + 1;
                    throw new BusinessException("Please wait " + wait + " s before requesting another code.");
                }
            }
            Deque<Instant> recent = issued.computeIfAbsent(key, k -> new ArrayDeque<>());
            recent.removeIf(at -> !at.isAfter(now.minus(WINDOW)));
            if (recent.size() >= MAX_CODES_PER_HOUR) {
                throw new BusinessException("Too many codes requested. Please try again in an hour.");
            }
            recent.addLast(now);
            challenges.put(key, challenge);
        }
        try {
            sender.sendOtp(purpose, email, code, config.ttl());
        } catch (BusinessException e) {
            synchronized (this) { // not delivered, so allow an immediate retry
                challenges.remove(key, challenge);
                Deque<Instant> recent = issued.get(key);
                if (recent != null) {
                    recent.removeLastOccurrence(now);
                }
            }
            throw e;
        }
        return new IssuedOtp(code, challenge.expiresAt());
    }

    /** @throws BusinessException when the code is wrong, expired or out of attempts */
    public synchronized void verify(OtpPurpose purpose, String email, String code) {
        String key = key(purpose, email);
        Challenge challenge = challenges.get(key);
        if (challenge == null || !challenge.expiresAt().isAfter(clock.instant())) {
            challenges.remove(key);
            throw new BusinessException("The code has expired. Please request a new one.");
        }
        String candidate = hash(key, code == null ? "" : code.trim());
        boolean matches = MessageDigest.isEqual(
                challenge.hash().getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int left = challenge.attemptsLeft() - 1;
            if (left <= 0) {
                challenges.remove(key);
                throw new BusinessException("Too many wrong attempts. Please request a new code.");
            }
            challenges.put(key, new Challenge(challenge.hash(), challenge.expiresAt(), left, challenge.issuedAt()));
            throw new BusinessException("Incorrect code. " + left + (left == 1 ? " attempt left." : " attempts left."));
        }
        challenges.remove(key);
    }

    @Scheduled(fixedDelay = 300_000)
    synchronized void purgeExpired() {
        Instant now = clock.instant();
        challenges.values().removeIf(c -> !c.expiresAt().isAfter(now));
        issued.values().removeIf(recent -> {
            recent.removeIf(at -> !at.isAfter(now.minus(WINDOW)));
            return recent.isEmpty();
        });
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, config.length());
        return String.format("%0" + config.length() + "d", random.nextInt(bound));
    }

    private static String key(OtpPurpose purpose, String email) {
        return purpose.name() + ':' + email;
    }

    private static String hash(String key, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((key + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
