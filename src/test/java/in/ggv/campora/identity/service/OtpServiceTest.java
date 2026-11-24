package in.ggv.campora.identity.service;

import static in.ggv.campora.identity.service.OtpPurpose.RESET_PASSWORD;
import static in.ggv.campora.identity.service.OtpPurpose.VERIFY_EMAIL;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;

class OtpServiceTest {

    private static final String EMAIL = "a@example.com";

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private boolean mailDown;

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private final OtpService otp = new OtpService(
            new CamporaProperties("UTC", false,
                    new CamporaProperties.Otp(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(30), false),
                    null, null, null, null, null),
            (purpose, email, code, validity) -> {
                if (mailDown) {
                    throw new BusinessException("down");
                }
            }, clock);

    @Test
    void codeWorksOnceAndOnlyForItsPurpose() {
        String code = otp.issue(VERIFY_EMAIL, EMAIL).code();
        assertThatThrownBy(() -> otp.verify(RESET_PASSWORD, EMAIL, code)).isInstanceOf(BusinessException.class);
        otp.verify(VERIFY_EMAIL, EMAIL, code);
        assertThatThrownBy(() -> otp.verify(VERIFY_EMAIL, EMAIL, code)).isInstanceOf(BusinessException.class);
    }

    @Test
    void hourlyCapSurvivesUsedUpCodes() {
        for (int i = 0; i < OtpService.MAX_CODES_PER_HOUR; i++) {
            otp.issue(RESET_PASSWORD, EMAIL);
            for (int guess = 0; guess < 5; guess++) {
                assertThatThrownBy(() -> otp.verify(RESET_PASSWORD, EMAIL, "wrong"))
                        .isInstanceOf(BusinessException.class);
            }
        }
        assertThatThrownBy(() -> otp.issue(RESET_PASSWORD, EMAIL)).hasMessageContaining("Too many codes");
        assertThatCode(() -> otp.issue(RESET_PASSWORD, "other@example.com")).doesNotThrowAnyException();
        now = now.plus(Duration.ofMinutes(61));
        assertThatCode(() -> otp.issue(RESET_PASSWORD, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void failedDeliveryDoesNotCount() {
        mailDown = true;
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> otp.issue(VERIFY_EMAIL, EMAIL)).hasMessage("down");
        }
        mailDown = false;
        assertThatCode(() -> otp.issue(VERIFY_EMAIL, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void resendIsThrottledAndCodesExpire() {
        String code = otp.issue(VERIFY_EMAIL, EMAIL).code();
        assertThatThrownBy(() -> otp.issue(VERIFY_EMAIL, EMAIL)).hasMessageContaining("Please wait");
        now = now.plus(Duration.ofMinutes(6));
        assertThatThrownBy(() -> otp.verify(VERIFY_EMAIL, EMAIL, code)).hasMessageContaining("expired");
    }
}
