package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
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

    public AccountServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll().stream()
                .peek(this::reconcileAccountBalance)
                .toList();
    }

    @Override
    public Optional<Account> getAccountById(Long id) {
        return Optional.ofNullable(accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id)));
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

        BigDecimal totalBalance = transactionRepository.findByAccountNumber(account.getAccountNumber()).stream()
                .filter(transaction -> transaction != null && transaction.getAmount() != null)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalizedBalance = transactionRepository.findByAccountNumber(account.getAccountNumber()).stream()
                .filter(transaction -> transaction != null
                        && transaction.getAmount() != null
                        && isBalanceImpactStatus(transaction.getStatus()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        account.setBalance(totalBalance);
        account.setAvailableBalance(finalizedBalance);
        accountRepository.save(account);
    }

    private boolean isBalanceImpactStatus(String status) {
        if (status == null) {
            return false;
        }

        String normalized = status.trim().toUpperCase();
        return "FINALISED".equals(normalized)
                || "FINALIZED".equals(normalized);
    }
}
