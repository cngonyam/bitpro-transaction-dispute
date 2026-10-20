package za.co.transactiondispute.integration;

import za.co.transactiondispute.dto.TransactionDto;

import java.time.LocalDateTime;
import java.util.List;

/** Source of truth for posted transactions (the core banking / card switch). */
public interface CoreBankingClient {

    List<TransactionDto> fetchTransactions(String accountNumber, LocalDateTime from, LocalDateTime to);
}
