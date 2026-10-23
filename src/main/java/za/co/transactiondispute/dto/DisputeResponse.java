package za.co.transactiondispute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {
    private Long id;
    private String disputeId;
    private String transactionReference;
    private String accountNumber;
    private BigDecimal disputedAmount;
    private String reasonCode;
    private DisputeType disputeType;
    private String description;
    private String assignedToFullName;
    private Boolean provisionalCreditIssued;
    private Boolean merchantEvidenceProvided;
    private String decisionOutcome;
    private DisputeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
