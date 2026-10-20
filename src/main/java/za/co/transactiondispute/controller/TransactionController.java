package za.co.transactiondispute.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.TransactionService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public PageResponse<TransactionDto> getAccountTransactions(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean refresh,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return transactionService.getAccountTransactions(accountNumber, from, to, refresh, page, size, Actor.from(authentication));
    }

    @GetMapping("/transactions/{reference}")
    public TransactionDto getTransaction(@PathVariable String reference, Authentication authentication) {
        return transactionService.getTransaction(reference, Actor.from(authentication));
    }
}
