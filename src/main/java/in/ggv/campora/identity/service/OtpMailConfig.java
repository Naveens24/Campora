package in.ggv.campora.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;

/**
 * SMTP when a sender address is configured; otherwise the log (dev/test).
 * Without one in production, sending fails visibly instead of pretending
 * a code is on its way.
 */
@Configuration
class OtpMailConfig {

    private static final Logger log = LoggerFactory.getLogger(OtpMailConfig.class);

    @Bean
    OtpSender otpSender(CamporaProperties properties, JavaMailSender mail) {
        String from = properties.mail().from();
        if (!from.isBlank()) {
            return (email, code, validity) -> {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(email);
                message.setSubject("Your Campora verification code");
                message.setText("Your Campora verification code is " + code + ".\n\nIt expires in "
                        + validity.toMinutes() + " minutes. If you didn't request it, ignore this email.");
                try {
                    mail.send(message);
                } catch (MailException e) {
                    log.error("Verification email could not be sent: {}", e.getMessage());
                    throw new BusinessException("We couldn't send your code right now. Please try again in a moment.");
                }
            };
        }
        if (properties.otp().exposeInUi()) {
            return (email, code, validity) -> log.info("[mail] Campora verification code {}", code);
        }
        log.error("MAIL_USERNAME is not set: verification codes cannot be sent.");
        return (email, code, validity) -> {
            throw new BusinessException("We couldn't send your code right now. Please try again later.");
        };
    }
}
