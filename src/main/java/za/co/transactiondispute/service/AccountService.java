package za.co.transactiondispute.service;

import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.security.Actor;

import java.util.List;

public interface AccountService {
    List<AccountDto> getAccounts(Actor actor);

    AccountDto getAccount(String accountNumber, Actor actor);

    /** Returns the account if the actor owns it (or is an agent); otherwise behaves as not found. */
    Account getAuthorisedAccount(String accountNumber, Actor actor);

    AccountDto createAccount(AccountDto request);
}
