package za.co.transactiondispute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String description;
}
