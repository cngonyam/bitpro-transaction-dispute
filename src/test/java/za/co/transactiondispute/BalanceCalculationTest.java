package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.util.BalanceCalculator;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceCalculationTest {

    @Test
    void shouldAggregateFinalizedAndAvailableBalancesUsingBalanceImpactStatuses() {
        List<Transaction> transactions = List.of(
                Transaction.builder()
                        .amount(new BigDecimal("100.00"))
                        .type(TransactionType.CREDIT)
                        .status("RESOLVED")
                        .build(),
                Transaction.builder()
                        .amount(new BigDecimal("50.00"))
                        .type(TransactionType.DEBIT)
                        .status("POSTED")
                        .build(),
                Transaction.builder()
                        .amount(new BigDecimal("25.00"))
                        .type(TransactionType.CREDIT)
                        .status("SETTLED")
                        .build()
        );

        assertEquals(new BigDecimal("125.00"), BalanceCalculator.calculateFinalizedBalance(transactions));
        assertEquals(new BigDecimal("75.00"), BalanceCalculator.calculateAvailableBalance(transactions));
    }
}
