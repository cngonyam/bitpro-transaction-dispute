package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDto> getAccounts(Authentication authentication) {
        return accountService.getAccounts(Actor.from(authentication));
    }

    @GetMapping("/{accountNumber}")
    public AccountDto getAccount(@PathVariable String accountNumber, Authentication authentication) {
        return accountService.getAccount(accountNumber, Actor.from(authentication));
    }

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody AccountDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }
}
