package za.co.transactiondispute.service;

import za.co.transactiondispute.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountService {
    List<Account> getAllAccounts();
    Optional<Account> getAccountById(Long id);
    Optional<Account> getAccountByNumber(String accountNumber);
    Account createAccount(Account account);
}
