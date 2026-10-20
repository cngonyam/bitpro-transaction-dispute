package za.co.transactiondispute.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsClient {

    private static final Logger log = LoggerFactory.getLogger(SmsClient.class);

    // Replace with a real SMS gateway
    public void sendSms(String to, String message) {
        log.info("SMS '{}' (recipient redacted)", message);
    }
}
