package za.co.transactiondispute.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class CustomerAccountContext {

    private static final Map<String, String> ACCOUNT_BY_USERNAME = Map.of(
            "dev-user", "ACC-1001",
            "customer-1", "ACC-2000",
            "customer-2", "ACC-3000"
    );

    private final String currentAccountNumber;

    public CustomerAccountContext() {
        this(null);
    }

    public CustomerAccountContext(String currentAccountNumber) {
        this.currentAccountNumber = currentAccountNumber;
    }

    public String getCurrentAccountNumber() {
        if (currentAccountNumber != null && !currentAccountNumber.isBlank()) {
            return currentAccountNumber;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "dev-user";
        return resolve(username);
    }

    public String getCurrentAccountNumber(String username) {
        return resolve(username);
    }

    private String resolve(String username) {
        if (username == null || username.isBlank()) {
            return "ACC-1001";
        }

        return ACCOUNT_BY_USERNAME.getOrDefault(username.trim().toLowerCase(Locale.ROOT), "ACC-1001");
    }
}
