package za.co.transactiondispute.security;

import org.springframework.security.core.Authentication;

public record Actor(String username, boolean agent) {

    public static Actor from(Authentication authentication) {
        boolean agent = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
        return new Actor(authentication.getName(), agent);
    }
}
