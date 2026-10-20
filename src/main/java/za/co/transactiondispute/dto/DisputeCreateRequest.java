package za.co.transactiondispute.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeReason;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCreateRequest {

    @NotBlank
    private String transactionReference;

    @NotBlank
    private String accountNumber;

    @NotNull
    @DecimalMin(value = "0.01", message = "must be greater than zero")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal disputedAmount;

    @NotNull
    private DisputeReason reasonCode;

    @Size(max = 1000)
    private String description;
}
