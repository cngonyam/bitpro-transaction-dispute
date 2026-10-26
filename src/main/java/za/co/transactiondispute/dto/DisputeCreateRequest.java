package za.co.transactiondispute.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @NotBlank(message = "Transaction reference is required")
    private String transactionReference;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Disputed amount is required")
    @Positive(message = "Disputed amount must be positive")
    private BigDecimal disputedAmount;

    @NotBlank(message = "Reason code is required")
    private String reasonCode;

    @NotNull(message = "Dispute type is required")
    private DisputeType disputeType;

    @NotBlank(message = "Description is required")
    private String description;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean provisionalCreditIssued;
    private Boolean merchantEvidenceProvided;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String decisionOutcome;
}
