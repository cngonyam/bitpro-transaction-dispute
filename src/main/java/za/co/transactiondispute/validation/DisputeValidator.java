package za.co.transactiondispute.validation;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.enums.DisputeType;

@Component
public class DisputeValidator {

    public void validateCreateRequest(DisputeCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dispute request is required");
        }
        if (request.getTransactionReference() == null || request.getTransactionReference().isBlank()) {
            throw new IllegalArgumentException("Transaction reference is required");
        }
        if (request.getAccountNumber() == null || request.getAccountNumber().isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (request.getDisputedAmount() == null || request.getDisputedAmount().signum() <= 0) {
            throw new IllegalArgumentException("Disputed amount must be greater than zero");
        }
        if (request.getReasonCode() == null || request.getReasonCode().isBlank()) {
            throw new IllegalArgumentException("Reason code is required");
        }
        if (request.getDisputeType() == null) {
            throw new IllegalArgumentException("Dispute type is required");
        }
    }
}
