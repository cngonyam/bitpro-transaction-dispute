package za.co.transactiondispute.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.exception.ConflictException;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.mapper.AccountMapper;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.AccountService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Override
    public List<AccountDto> getAccounts(Actor actor) {
        List<Account> accounts = actor.agent()
                ? accountRepository.findAll()
                : accountRepository.findByOwnerUsername(actor.username());
        return accounts.stream().map(accountMapper::toDto).toList();
    }

    @Override
    public AccountDto getAccount(String accountNumber, Actor actor) {
        return accountMapper.toDto(getAuthorisedAccount(accountNumber, actor));
    }

    @Override
    public Account getAuthorisedAccount(String accountNumber, Actor actor) {
        return accountRepository.findByAccountNumber(accountNumber)
                .filter(a -> actor.agent() || a.getOwnerUsername().equals(actor.username()))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    @Override
    public AccountDto createAccount(AccountDto request) {
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new ConflictException("Account already exists: " + request.getAccountNumber());
        }
        return accountMapper.toDto(accountRepository.save(accountMapper.toEntity(request)));
    }
}
