package za.co.transactiondispute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeType;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCreateRequest {
    private String transactionReference;
    private String accountNumber;
    private BigDecimal disputedAmount;
    private String reasonCode;
    private DisputeType disputeType;
    private String description;
    private Boolean provisionalCreditIssued;
    private Boolean merchantEvidenceProvided;
    private String decisionOutcome;
}
