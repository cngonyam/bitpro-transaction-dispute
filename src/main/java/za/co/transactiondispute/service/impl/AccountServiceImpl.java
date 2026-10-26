package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.util.BalanceCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerAccountContext customerAccountContext;

    public AccountServiceImpl(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             CustomerAccountContext customerAccountContext) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.customerAccountContext = customerAccountContext;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        return accountRepository.findAll().stream()
                .filter(account -> account != null && account.getAccountNumber() != null
                        && account.getAccountNumber().equalsIgnoreCase(currentAccountNumber))
                .map(this::copyWithDerivedBalances)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> getAccountById(Long id) {
        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));

        if (account.getAccountNumber() == null || !account.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new ResourceNotFoundException("Account not found with id: " + id);
        }

        return Optional.of(copyWithDerivedBalances(account));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> getAccountByNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return Optional.empty();
        }

        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        return accountRepository.findByAccountNumber(accountNumber)
                .filter(account -> account.getAccountNumber() != null
                        && account.getAccountNumber().equalsIgnoreCase(currentAccountNumber))
                .map(this::copyWithDerivedBalances);
    }

    @Override
    @Transactional
    public Account createAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account payload is required");
        }
        return accountRepository.save(account);
    }

    private Account copyWithDerivedBalances(Account account) {
        if (account == null || account.getAccountNumber() == null) {
            return account;
        }

        List<Transaction> accountTransactions = transactionRepository.findByAccountNumber(account.getAccountNumber());
        BigDecimal finalizedBalance = BalanceCalculator.calculateFinalizedBalance(accountTransactions);
        BigDecimal availableBalance = BalanceCalculator.calculateAvailableBalance(accountTransactions);

        Account derived = new Account();
        derived.setId(account.getId());
        derived.setVersion(account.getVersion());
        derived.setAccountNumber(account.getAccountNumber());
        derived.setCustomerName(account.getCustomerName());
        derived.setCurrency(account.getCurrency());
        derived.setAvailableBalance(availableBalance);
        derived.setBalance(finalizedBalance);
        derived.setStatus(account.getStatus());
        return derived;
    }
}
