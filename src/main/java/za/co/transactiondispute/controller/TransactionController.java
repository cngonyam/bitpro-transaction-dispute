package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.TransactionService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;
    private final AccountService accountService;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper, AccountService accountService) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
        this.accountService = accountService;
    }

    @GetMapping("/transactions")
    public Page<TransactionDto> getTransactions(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        List<TransactionDto> transactions = transactionService.getAllTransactions().stream()
                .map(this::toDtoWithAccountBalance)
                .toList();
        return pageFromList(transactions, pageable);
    }

    @GetMapping("/transactions/{reference}")
    public ResponseEntity<TransactionDto> getTransaction(@PathVariable String reference) {
        return ResponseEntity.ok(toDtoWithAccountBalance(transactionService.getTransactionByReference(reference).orElseThrow()));
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionDto> createTransaction(@Valid @RequestBody TransactionDto transactionDto) {
        Transaction transaction = transactionMapper.toEntity(transactionDto);
        Transaction saved = transactionService.createTransaction(transaction);
        URI location;
        try {
            location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{reference}")
                    .buildAndExpand(saved.getTransactionReference())
                    .toUri();
        } catch (IllegalStateException ex) {
            location = URI.create("/api/v1/transactions/" + saved.getTransactionReference());
        }
        return ResponseEntity.created(location).body(toDtoWithAccountBalance(saved));
    }

    private TransactionDto toDtoWithAccountBalance(Transaction transaction) {
        TransactionDto dto = transactionMapper.toDto(transaction);
        accountService.getAccountByNumber(transaction.getAccountNumber())
                .ifPresent(account -> dto.setAccountBalance(account.getAvailableBalance() != null ? account.getAvailableBalance() : account.getBalance()));
        return dto;
    }

    private Page<TransactionDto> pageFromList(List<TransactionDto> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content, Pageable.unpaged(), content.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), content.size());
        List<TransactionDto> pageContent = (start >= content.size()) ? List.of() : content.subList(start, end);
        return new PageImpl<>(pageContent, pageable, content.size());
    }
}
