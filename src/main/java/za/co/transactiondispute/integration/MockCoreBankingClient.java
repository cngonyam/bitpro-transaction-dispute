package za.co.transactiondispute.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.enums.TransactionStatus;
import za.co.transactiondispute.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/** Deterministic fake transaction feed per account, for local development and tests. */
@Component
@ConditionalOnProperty(name = "core-banking.mode", havingValue = "mock", matchIfMissing = true)
public class MockCoreBankingClient implements CoreBankingClient {

    private static final String[] MERCHANTS = {
            "Pick n Pay", "Woolworths", "Checkers", "Engen Garage", "Uber Eats", "Takealot.com",
            "Netflix", "Vodacom Airtime", "Dis-Chem", "Mr Price", "Clicks", "Showmax"
    };

    @Override
    public List<TransactionDto> fetchTransactions(String accountNumber, LocalDateTime from, LocalDateTime to) {
        Random random = new Random(accountNumber.hashCode());
        LocalDate today = LocalDate.now();

        // Sequential stream keeps the Random draw order, so output stays deterministic.
        return IntStream.range(0, 30).mapToObj(i -> {
            LocalDateTime date = today.minusDays(random.nextInt(60)).atTime(6 + random.nextInt(16), random.nextInt(60));
            if (date.isAfter(LocalDateTime.now())) {
                date = date.minusDays(1);
            }
            boolean credit = i % 10 == 0;
            boolean atm = !credit && i % 7 == 3;
            String merchant = credit ? "Salary Deposit" : atm ? "ATM Withdrawal" : MERCHANTS[random.nextInt(MERCHANTS.length)];
            BigDecimal amount = BigDecimal.valueOf(2000L + random.nextInt(150000), 2);
            if (credit) {
                amount = amount.multiply(BigDecimal.TEN);
            }

            return TransactionDto.builder()
                    .transactionReference(String.format("TXN%s%04d", accountNumber, i))
                    .accountNumber(accountNumber)
                    .amount(amount)
                    .currency("ZAR")
                    .type(credit ? TransactionType.CREDIT : TransactionType.DEBIT)
                    .merchantName(merchant)
                    .status(i % 11 == 7 ? TransactionStatus.PENDING : TransactionStatus.POSTED)
                    .transactionDate(date)
                    .description(credit ? "Monthly salary" : "Card purchase at " + merchant)
                    .build();
        })
                .filter(t -> !t.getTransactionDate().isBefore(from) && t.getTransactionDate().isBefore(to))
                .toList();
    }
}
