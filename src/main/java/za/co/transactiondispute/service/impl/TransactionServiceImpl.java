package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final CustomerAccountContext customerAccountContext;
    private final AccountRepository accountRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                 CustomerAccountContext customerAccountContext,
                                 AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.customerAccountContext = customerAccountContext;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findByAccountNumber(customerAccountContext.getCurrentAccountNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> getTransactionByReference(String reference) {
        return transactionRepository.findByTransactionReference(reference)
                .filter(transaction -> transaction.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber()))
                .or(() -> {
                    throw new ResourceNotFoundException("Transaction not found with reference: " + reference);
                });
    }

    @Override
    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction payload is required");
        }
        if (transaction.getType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (transaction.getType() == TransactionType.CREDIT) {
            throw new IllegalArgumentException("Customers cannot create credit transactions.");
        }
        if (!transaction.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber())) {
            throw new IllegalArgumentException("Customers can only access their own account transactions.");
        }

        transaction.setTransactionReference(generateTransactionReference());
        transaction.setAmount(normalizeAmount(transaction.getType(), transaction.getAmount()));

        Account account = accountRepository.findByAccountNumber(transaction.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + transaction.getAccountNumber()));

        Transaction savedTransaction = transactionRepository.save(transaction);
        reconcileAccountBalance(transaction.getAccountNumber(), account);
        return savedTransaction;
    }

    private void reconcileAccountBalance(String accountNumber) {
        reconcileAccountBalance(accountNumber, accountRepository.findByAccountNumber(accountNumber).orElse(null));
    }

    private void reconcileAccountBalance(String accountNumber, Account account) {
        if (account == null) {
            account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        }
        if (account == null) {
            return;
        }

        List<Transaction> accountTransactions = transactionRepository.findByAccountNumber(accountNumber);

        BigDecimal finalizedBalance = accountTransactions.stream()
                .filter(transaction -> transaction != null
                        && transaction.getAmount() != null
                        && isBalanceImpactStatus(transaction.getStatus()))
                .map(this::normalizeBalanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableBalance = accountTransactions.stream()
                .filter(transaction -> transaction != null && transaction.getAmount() != null)
                .map(this::normalizeBalanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        account.setBalance(finalizedBalance);
        account.setAvailableBalance(availableBalance);
        accountRepository.save(account);
    }

    private BigDecimal normalizeBalanceAmount(Transaction transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal magnitude = transaction.getAmount().abs();
        return transaction.getType() == TransactionType.DEBIT ? magnitude.negate() : magnitude;
    }

    private String generateTransactionReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TX-" + datePart + "-" + randomPart;
    }

    private BigDecimal normalizeTransactionAmount(Transaction transaction) {
        return normalizeAmount(transaction.getType(), transaction.getAmount());
    }

    private boolean isBalanceImpactStatus(String status) {
        if (status == null) {
            return false;
        }

        String normalized = status.trim().toUpperCase();
        return "RESOLVED".equals(normalized)
                || "SETTLED".equals(normalized)
                || "FINALIZED".equals(normalized)
                || "FINALISED".equals(normalized);
    }

    private BigDecimal normalizeAmount(TransactionType type, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = amount.abs();
        return type == TransactionType.DEBIT ? value.negate() : value;
    }
}
