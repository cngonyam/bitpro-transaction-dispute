package za.co.transactiondispute.enums;

public enum DisputeReason {
    UNAUTHORISED_TRANSACTION("I did not make or authorise this transaction"),
    CARD_LOST_OR_STOLEN("My card was lost or stolen"),
    DUPLICATE_CHARGE("I was charged more than once"),
    INCORRECT_AMOUNT("The amount charged is incorrect"),
    GOODS_NOT_RECEIVED("I did not receive the goods"),
    SERVICE_NOT_RENDERED("I did not receive the service"),
    ATM_CASH_NOT_DISPENSED("The ATM did not dispense the cash"),
    REFUND_NOT_RECEIVED("I did not receive my refund"),
    OTHER("Other");

    private final String label;

    DisputeReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
