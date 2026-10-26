package za.co.transactiondispute.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotBlank(message = "Transaction reference is required")
    private String transactionReference;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    private BigDecimal accountBalance;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Type is required")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private TransactionType type;

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    @NotBlank(message = "Status is required")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    private LocalDateTime transactionDate;
    private String description;
}
