package za.co.transactiondispute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeStatus;

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
    private String description;
    private String assignedToFullName;
    private DisputeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
