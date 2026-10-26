package za.co.transactiondispute.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.AccountService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerAccountContext customerAccountContext;

    public AccountServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this(accountRepository, transactionRepository, new CustomerAccountContext());
    }

    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             CustomerAccountContext customerAccountContext) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.customerAccountContext = customerAccountContext;
    }

    @Override
    public List<Account> getAllAccounts() {
        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        return accountRepository.findAll().stream()
                .filter(account -> account != null && account.getAccountNumber() != null
                        && account.getAccountNumber().equalsIgnoreCase(currentAccountNumber))
                .peek(this::reconcileAccountBalance)
                .toList();
    }

    @Override
    public Optional<Account> getAccountById(Long id) {
        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));

        if (account.getAccountNumber() == null || !account.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new ResourceNotFoundException("Account not found with id: " + id);
        }

        reconcileAccountBalance(account);
        return Optional.of(account);
    }

    @Override
    public Account createAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account payload is required");
        }
        return accountRepository.save(account);
    }

    private void reconcileAccountBalance(Account account) {
        if (account == null || account.getAccountNumber() == null) {
            return;
        }

        List<Transaction> accountTransactions = transactionRepository.findByAccountNumber(account.getAccountNumber());

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
}
