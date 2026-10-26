package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.service.AccountService;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts")
    public Page<AccountDto> getAccounts(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        List<AccountDto> accounts = accountService.getAllAccounts().stream()
                .map(this::toDto)
                .toList();
        return pageFromList(accounts, pageable);
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(toDto(accountService.getAccountById(id).orElseThrow()));
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody AccountDto accountDto) {
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

        Account saved = accountService.createAccount(account);
        URI location;
        try {
            location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(saved.getId())
                    .toUri();
        } catch (IllegalStateException ex) {
            location = URI.create("/api/v1/accounts/" + saved.getId());
        }

        return ResponseEntity.created(location).body(toDto(saved));
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .customerName(account.getCustomerName())
                .currency(account.getCurrency())
                .availableBalance(account.getAvailableBalance())
                .balance(account.getBalance())
                .status(account.getStatus())
                .build();
    }

    private Page<AccountDto> pageFromList(List<AccountDto> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content, Pageable.unpaged(), content.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), content.size());
        List<AccountDto> pageContent = (start >= content.size()) ? List.of() : content.subList(start, end);
        return new PageImpl<>(pageContent, pageable, content.size());
    }
}
