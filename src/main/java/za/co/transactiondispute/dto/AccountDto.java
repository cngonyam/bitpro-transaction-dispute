package za.co.transactiondispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private Long id;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Currency is required")
    private String currency;

    private BigDecimal availableBalance;
    private BigDecimal balance;

    @NotBlank(message = "Status is required")
    private String status;
}
