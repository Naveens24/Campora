package in.ggv.campora.identity.service;

import java.time.Duration;

/** Delivers one-time codes by email. {@link OtpMailConfig} picks the implementation. */
@FunctionalInterface
public interface OtpSender {

    /** @throws in.ggv.campora.common.exception.BusinessException when the code could not be sent */
    void sendOtp(OtpPurpose purpose, String email, String code, Duration validity);
}
