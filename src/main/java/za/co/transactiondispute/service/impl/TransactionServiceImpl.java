package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.TransactionService;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final CustomerAccountContext customerAccountContext;

    public TransactionServiceImpl(TransactionRepository transactionRepository, CustomerAccountContext customerAccountContext) {
        this.transactionRepository = transactionRepository;
        this.customerAccountContext = customerAccountContext;
    }

    @Override
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findByAccountNumber(customerAccountContext.getCurrentAccountNumber());
    }

    @Override
    public Optional<Transaction> getTransactionByReference(String reference) {
        return transactionRepository.findByTransactionReference(reference)
                .filter(transaction -> transaction.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber()))
                .or(() -> {
                    throw new ResourceNotFoundException("Transaction not found with reference: " + reference);
                });
    }

    @Override
    public Transaction createTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction payload is required");
        }
        if (!transaction.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber())) {
            throw new IllegalArgumentException("Customers can only access their own account transactions.");
        }
        return transactionRepository.save(transaction);
    }
}
