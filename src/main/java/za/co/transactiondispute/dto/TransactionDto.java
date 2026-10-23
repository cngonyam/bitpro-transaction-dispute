package za.co.transactiondispute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private Long id;
    private String transactionReference;
    private String accountNumber;
    private BigDecimal accountBalance;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private String merchantName;
    private String status;
    private LocalDateTime transactionDate;
    private String description;
}
