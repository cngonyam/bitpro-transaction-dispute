package za.co.transactiondispute.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomerAccountContext {

    private final String currentAccountNumber;

    public CustomerAccountContext(@Value("${app.dev.account-number:ACC-1001}") String currentAccountNumber) {
        this.currentAccountNumber = currentAccountNumber;
    }

    public String getCurrentAccountNumber() {
        return currentAccountNumber;
    }
}
