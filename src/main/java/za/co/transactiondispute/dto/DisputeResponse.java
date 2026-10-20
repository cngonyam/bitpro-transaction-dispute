package za.co.transactiondispute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeReason;
import za.co.transactiondispute.enums.DisputeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisputeResponse {
    private String disputeId;
    private String transactionReference;
    private String accountNumber;
    private BigDecimal disputedAmount;
    private String currency;
    private DisputeReason reasonCode;
    private String description;
    private DisputeStatus status;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private List<DisputeEventDto> history;
}
