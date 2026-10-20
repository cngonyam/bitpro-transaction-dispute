package za.co.transactiondispute.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailClient {

    private static final Logger log = LoggerFactory.getLogger(EmailClient.class);

    // Replace with a real provider (SES, SendGrid, ...)
    public void sendEmail(String to, String subject, String body) {
        log.info("EMAIL subject='{}' (recipient redacted)", subject);
    }
}
