package za.co.transactiondispute.service;

import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.security.Actor;

import java.time.LocalDate;

public interface TransactionService {

    /** Lists an account's transactions; when refresh is true, new ones are first pulled from core banking. */
    PageResponse<TransactionDto> getAccountTransactions(String accountNumber, LocalDate from, LocalDate to,
                                                        boolean refresh, int page, int size, Actor actor);

    TransactionDto getTransaction(String reference, Actor actor);

    /** Finds a transaction that belongs to the account, pulling from core banking if not yet stored locally. */
    Transaction getTransactionForAccount(String reference, String accountNumber);
}
