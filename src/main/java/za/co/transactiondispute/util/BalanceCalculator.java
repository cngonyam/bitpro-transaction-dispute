package za.co.transactiondispute.util;

import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BalanceCalculator {

    public static final Set<String> BALANCE_IMPACT_STATUSES = Set.of(
            "RESOLVED",
            "SETTLED",
            "FINALIZED",
            "FINALISED"
    );

    private BalanceCalculator() {
    }

    public static BigDecimal calculateFinalizedBalance(List<Transaction> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction != null
                        && transaction.getAmount() != null
                        && isBalanceImpactStatus(transaction.getStatus()))
                .map(BalanceCalculator::normalizeBalanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal calculateAvailableBalance(List<Transaction> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction != null && transaction.getAmount() != null)
                .map(BalanceCalculator::normalizeBalanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static boolean isBalanceImpactStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized = canonicaliseStatus(status);
        return BALANCE_IMPACT_STATUSES.contains(normalized);
    }

    public static String canonicaliseStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "FINALISED", "FINALIZED" -> "FINALIZED";
            case "SETTLED" -> "SETTLED";
            case "RESOLVED" -> "RESOLVED";
            default -> normalized;
        };
    }

    private static BigDecimal normalizeBalanceAmount(Transaction transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal magnitude = transaction.getAmount().abs();
        return transaction.getType() == TransactionType.DEBIT ? magnitude.negate() : magnitude;
    }
}
