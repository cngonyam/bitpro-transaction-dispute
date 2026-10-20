package za.co.transactiondispute.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.AccountStatus;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "must be 10 digits")
    private String accountNumber;

    @NotBlank
    private String ownerUsername;

    @NotBlank
    private String customerName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "\\+\\d{10,15}", message = "must be in international format, e.g. +27821234567")
    private String mobileNumber;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
    private String currency;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal availableBalance;

    private AccountStatus status;
}
