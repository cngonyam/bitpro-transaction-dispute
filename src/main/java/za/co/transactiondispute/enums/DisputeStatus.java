package za.co.transactiondispute.enums;

import java.util.Map;
import java.util.Set;

public enum DisputeStatus {
    OPEN,
    IN_REVIEW,
    PROVISIONAL_CREDIT_ISSUED,
    MERCHANT_RESPONSE_PENDING,
    RESOLVED,
    REJECTED;

    private static final Map<DisputeStatus, Set<DisputeStatus>> ALLOWED_TRANSITIONS = Map.of(
            OPEN, Set.of(IN_REVIEW, RESOLVED),
            IN_REVIEW, Set.of(PROVISIONAL_CREDIT_ISSUED, REJECTED),
            PROVISIONAL_CREDIT_ISSUED, Set.of(RESOLVED),
            MERCHANT_RESPONSE_PENDING, Set.of(RESOLVED, REJECTED),
            RESOLVED, Set.of(),
            REJECTED, Set.of()
    );

    public boolean canTransitionTo(DisputeStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(nextStatus);
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == REJECTED;
    }
}
