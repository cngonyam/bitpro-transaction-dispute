package za.co.transactiondispute.enums;

public enum DisputeStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED,
    CANCELLED;

    public boolean canTransitionTo(DisputeStatus target) {
        return switch (this) {
            case OPEN -> target == IN_REVIEW || target == REJECTED || target == CANCELLED;
            case IN_REVIEW -> target == RESOLVED || target == REJECTED;
            default -> false;
        };
    }
}
