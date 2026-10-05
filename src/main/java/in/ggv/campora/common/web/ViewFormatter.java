package in.ggv.campora.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.identity.service.MobileNumbers;

/**
 * Formatting helpers for templates, available as {@code @fmt} — e.g.
 * {@code ${@fmt.time(ride.departureTime())}}. All times render in campus time.
 */
@Component("fmt")
public class ViewFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ZoneId zone;
    private final Clock clock;

    public ViewFormatter(CamporaProperties properties, Clock clock) {
        this.zone = properties.zoneId();
        this.clock = clock;
    }

    public String time(Instant instant) {
        return instant == null ? "—" : TIME.format(instant.atZone(zone));
    }

    /** "Today, 4:30 PM" / "12 Sep, 9:05 AM". */
    public String when(Instant instant) {
        if (instant == null) {
            return "—";
        }
        ZonedDateTime at = instant.atZone(zone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (at.toLocalDate().equals(today)) {
            return "Today, " + TIME.format(at);
        }
        if (at.toLocalDate().equals(today.plusDays(1))) {
            return "Tomorrow, " + TIME.format(at);
        }
        return DATE_TIME.format(at);
    }

    /** "just now", "4 min ago", "2 h ago". */
    public String ago(Instant instant) {
        if (instant == null) {
            return "—";
        }
        long minutes = Duration.between(instant, clock.instant()).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        return (minutes / 60) + " h ago";
    }

    /** Minutes until an instant, never negative. */
    public long minutesUntil(Instant instant) {
        return instant == null ? 0 : Math.max(0, Duration.between(clock.instant(), instant).toMinutes());
    }

    /** Value for an {@code <input type="datetime-local">}, e.g. default departure in 30 min. */
    public String inputIn(int minutesFromNow) {
        return INPUT.format(clock.instant().plus(Duration.ofMinutes(minutesFromNow)).atZone(zone));
    }

    /** 1..min(count, cap) — drives the seat-dot bars. */
    public List<Integer> dots(Number count, int cap) {
        int n = count == null ? 0 : (int) Math.min(count.longValue(), cap);
        return IntStream.rangeClosed(1, n).boxed().toList();
    }

    public String mask(String mobile) {
        return MobileNumbers.mask(mobile);
    }

    /** "ENGINEERING_BLOCK" style enum names → "Engineering block". */
    public String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String s = value.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
