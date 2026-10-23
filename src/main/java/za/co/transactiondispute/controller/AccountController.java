package za.co.transactiondispute.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.service.AccountService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts")
    public List<Account> getAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id).orElseThrow());
    }

    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@RequestBody AccountDto accountDto) {
        BigDecimal availableBalance = accountDto.getAvailableBalance() == null ? BigDecimal.ZERO : accountDto.getAvailableBalance();
        BigDecimal balance = accountDto.getBalance() == null ? availableBalance : accountDto.getBalance();

        Account account = Account.builder()
                .accountNumber(accountDto.getAccountNumber())
                .customerName(accountDto.getCustomerName())
                .currency(accountDto.getCurrency())
                .availableBalance(availableBalance)
                .balance(balance)
                .status(accountDto.getStatus())
                .build();

        return ResponseEntity.ok(accountService.createAccount(account));
    }
}
