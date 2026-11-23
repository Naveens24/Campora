package in.ggv.campora.identity.service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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
            return (purpose, email, code, validity) -> {
                MimeMessage message = mail.createMimeMessage();
                try {
                    MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
                    helper.setFrom(from, "Campora");
                    helper.setTo(email);
                    helper.setSubject(purpose.subject());
                    helper.setText("Your Campora " + purpose.label() + " is " + code + ".\n\nIt expires in "
                            + validity.toMinutes() + " minutes. If you didn't request it, ignore this email.");
                    mail.send(message);
                } catch (MailException | MessagingException | UnsupportedEncodingException e) {
                    log.error("Code email could not be sent: {}", e.getMessage());
                    throw new BusinessException("We couldn't send your code right now. Please try again in a moment.");
                }
            };
        }
        if (properties.otp().exposeInUi()) {
            return (purpose, email, code, validity) -> log.info("[mail] Campora {} {}", purpose.label(), code);
        }
        log.error("MAIL_USERNAME is not set: verification codes cannot be sent.");
        return (purpose, email, code, validity) -> {
            throw new BusinessException("We couldn't send your code right now. Please try again later.");
        };
    }
}
