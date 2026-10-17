package za.co.transactiondispute.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.service.TransactionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @GetMapping("/transactions")
    public List<TransactionDto> getTransactions() {
        return transactionService.getAllTransactions().stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    @GetMapping("/transactions/{reference}")
    public ResponseEntity<TransactionDto> getTransaction(@PathVariable String reference) {
        return ResponseEntity.ok(transactionMapper.toDto(transactionService.getTransactionByReference(reference).orElseThrow()));
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionDto> createTransaction(@RequestBody TransactionDto transactionDto) {
        Transaction transaction = transactionMapper.toEntity(transactionDto);
        Transaction saved = transactionService.createTransaction(transaction);
        return ResponseEntity.ok(transactionMapper.toDto(saved));
    }
}
