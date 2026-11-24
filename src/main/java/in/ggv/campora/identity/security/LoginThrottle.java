package in.ggv.campora.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.service.MobileNumbers;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Slows down password guessing: too many wrong passwords for one account from
 * one address pause sign-in for that pair. Keyed by both, so a stranger cannot
 * lock the real owner out from elsewhere.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean blocked(HttpServletRequest request) {
        Deque<Instant> recent = failures.get(key(request));
        return recent != null && trim(recent).size() >= MAX_FAILURES;
    }

    public synchronized void failed(HttpServletRequest request) {
        trim(failures.computeIfAbsent(key(request), k -> new ArrayDeque<>())).addLast(clock.instant());
    }

    @Scheduled(fixedDelay = 300_000)
    synchronized void purge() {
        failures.values().removeIf(recent -> trim(recent).isEmpty());
    }

    private Deque<Instant> trim(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
        return recent;
    }

    private static String key(HttpServletRequest request) {
        String login = request.getParameter("mobile");
        login = login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
        if (!login.contains("@")) {
            try {
                login = MobileNumbers.normalize(login); // the same number typed differently is one account
            } catch (BusinessException notANumber) {
                // keep what was typed
            }
        }
        return request.getRemoteAddr() + '|' + (login.length() > 100 ? login.substring(0, 100) : login);
    }
}
