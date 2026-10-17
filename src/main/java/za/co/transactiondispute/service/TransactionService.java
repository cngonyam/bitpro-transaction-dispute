package za.co.transactiondispute.service;

import za.co.transactiondispute.entity.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionService {
    List<Transaction> getAllTransactions();
    Optional<Transaction> getTransactionByReference(String reference);
    Transaction createTransaction(Transaction transaction);
}
