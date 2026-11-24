package in.ggv.campora.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.service.MobileNumbers;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Slows down password guessing. Too many wrong passwords for one account from one
 * address pause sign-in for that pair; keyed by both, so a stranger cannot lock the
 * real owner out from elsewhere. A much higher limit per network stops one password
 * being tried on every account.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 10;
    static final int MAX_FAILURES_PER_NETWORK = 100;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final SlidingWindow accounts;
    private final SlidingWindow networks;

    public LoginThrottle(Clock clock) {
        this.accounts = new SlidingWindow(WINDOW, clock);
        this.networks = new SlidingWindow(WINDOW, clock);
    }

    public boolean blocked(HttpServletRequest request) {
        return accounts.count(key(request)) >= MAX_FAILURES
                || SlidingWindow.network(request)
                        .filter(network -> networks.count(network) >= MAX_FAILURES_PER_NETWORK).isPresent();
    }

    public void failed(HttpServletRequest request) {
        accounts.add(key(request));
        SlidingWindow.network(request).ifPresent(networks::add);
    }

    @Scheduled(fixedDelay = 300_000)
    void purge() {
        accounts.purge();
        networks.purge();
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
