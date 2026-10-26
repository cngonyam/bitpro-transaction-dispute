package za.co.transactiondispute.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CustomerAccountContext {

    private Map<String, String> accountByUsername = Map.of();
    private String currentAccountNumber;

    public CustomerAccountContext() {
        this(Map.of(), null);
    }

    public CustomerAccountContext(String currentAccountNumber) {
        this(Map.of(), currentAccountNumber);
    }

    public CustomerAccountContext(String accountMappings, String currentAccountNumber) {
        this(parseMappings(accountMappings), currentAccountNumber);
    }

    public CustomerAccountContext(Map<String, String> accountByUsername, String currentAccountNumber) {
        this.accountByUsername = accountByUsername == null ? Map.of() : accountByUsername;
        this.currentAccountNumber = currentAccountNumber;
    }

    @Value("${app.account-mappings:}")
    public void setAccountMappings(String accountMappings) {
        this.accountByUsername = parseMappings(accountMappings);
    }

    public String getCurrentAccountNumber() {
        if (currentAccountNumber != null && !currentAccountNumber.isBlank()) {
            return currentAccountNumber;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Authentication is required to resolve the customer account.");
        }

        return resolve(authentication.getName());
    }

    public String getCurrentAccountNumber(String username) {
        return resolve(username);
    }

    private String resolve(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authenticated username is required to resolve the account.");
        }

        String accountNumber = accountByUsername.get(username.trim().toLowerCase(Locale.ROOT));
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new AccessDeniedException("No account is mapped for user: " + username);
        }

        return accountNumber;
    }

    private static Map<String, String> parseMappings(String accountMappings) {
        if (accountMappings == null || accountMappings.isBlank()) {
            return Map.of();
        }

        return Arrays.stream(accountMappings.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(entry -> entry.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim().toLowerCase(Locale.ROOT),
                        parts -> parts[1].trim(),
                        (left, right) -> right
                ));
    }
}
