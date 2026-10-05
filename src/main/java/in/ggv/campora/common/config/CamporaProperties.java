package in.ggv.campora.common.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe view of the {@code campora.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "campora")
public record CamporaProperties(
        @DefaultValue("Asia/Kolkata") String zone,
        @DefaultValue("false") boolean seedDemoData,
        @DefaultValue Otp otp,
        @DefaultValue Mobility mobility,
        @DefaultValue Companion companion) {

    /** Campus time zone used to read and display local date-times. */
    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public record Otp(
            @DefaultValue("6") int length,
            @DefaultValue("PT5M") Duration ttl,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("PT30S") Duration resendCooldown,
            @DefaultValue("false") boolean exposeInUi) {
    }

    public record Mobility(
            @DefaultValue("PT45M") Duration requestTtl,
            @DefaultValue("4") int maxSeatsPerRequest,
            @DefaultValue("15") int hotspotRefreshSeconds,
            @DefaultValue("60000") long expirySweepMs) {
    }

    public record Companion(
            @DefaultValue("6") int maxSeats,
            @DefaultValue("PT15M") Duration joinGrace) {
    }
}
