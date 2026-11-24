package in.ggv.campora.identity.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import in.ggv.campora.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Limits how many code emails one network may ask for, so nobody can use Campora to
 * flood mailboxes or use up the mail account's daily allowance. Generous enough for a
 * whole class signing up on the campus Wi-Fi.
 */
@Component
public class CodeThrottle {

    static final int PER_HOUR = 60;
    static final int PER_DAY = 200;

    private final SlidingWindow hour;
    private final SlidingWindow day;

    public CodeThrottle(Clock clock) {
        this.hour = new SlidingWindow(Duration.ofHours(1), clock);
        this.day = new SlidingWindow(Duration.ofDays(1), clock);
    }

    /** Counts one request for a code. */
    public void check(HttpServletRequest request) {
        SlidingWindow.network(request).ifPresent(network -> {
            if (hour.count(network) >= PER_HOUR || day.count(network) >= PER_DAY) {
                throw new BusinessException("Too many codes were requested from your network. "
                        + "Try again later, or use mobile data.");
            }
            hour.add(network);
            day.add(network);
        });
    }

    @Scheduled(fixedDelay = 600_000)
    void purge() {
        hour.purge();
        day.purge();
    }
}
