package in.ggv.campora.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development SMS gateway: writes the message to the application log.
 * Replace this bean with a real provider implementation for production.
 */
@Component
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public void send(String mobile, String message) {
        log.info("[SMS → {}] {}", MobileNumbers.mask(mobile), message);
    }
}
