package za.co.transactiondispute.util;

import java.util.Locale;

public final class UserRoles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String AGENT = "AGENT";
    public static final String LEAD = "LEAD";
    public static final String ADMIN = "ADMIN";

    private UserRoles() {
    }

    public static String canonicalize(String role, String username) {
        String candidate = role == null ? "" : role.trim();
        if (candidate.isBlank()) {
            return defaultRoleForUsername(username);
        }

        String normalized = candidate.toUpperCase(Locale.ROOT);
        if (normalized.contains("ADMIN")) {
            return ADMIN;
        }
        if (normalized.contains("LEAD")) {
            return LEAD;
        }
        if (normalized.contains("AGENT") || normalized.contains("OPERATIONS")) {
            return AGENT;
        }
        return CUSTOMER;
    }

    public static String defaultRoleForUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains("admin")) {
            return ADMIN;
        }
        if (normalized.contains("lead")) {
            return LEAD;
        }
        if (normalized.contains("agent") || normalized.contains("operations") || normalized.contains("ops")) {
            return AGENT;
        }
        return CUSTOMER;
    }

    public static boolean isAgentRole(String role) {
        String canonical = canonicalize(role, null);
        return AGENT.equals(canonical) || LEAD.equals(canonical);
    }

    public static boolean isLeadRole(String role) {
        return LEAD.equals(canonicalize(role, null));
    }

    public static boolean isAdminRole(String role) {
        return ADMIN.equals(canonicalize(role, null));
    }
}
