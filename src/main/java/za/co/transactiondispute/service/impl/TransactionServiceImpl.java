package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.TransactionService;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @Override
    public Optional<Transaction> getTransactionByReference(String reference) {
        return Optional.ofNullable(transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + reference)));
    }

    @Override
    public Transaction createTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction payload is required");
        }
        return transactionRepository.save(transaction);
    }
}
