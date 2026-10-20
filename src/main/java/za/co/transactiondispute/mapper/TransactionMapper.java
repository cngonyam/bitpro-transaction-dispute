package za.co.transactiondispute.mapper;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;

@Component
public class TransactionMapper {

    public TransactionDto toDto(Transaction t) {
        return TransactionDto.builder()
                .transactionReference(t.getTransactionReference())
                .accountNumber(t.getAccountNumber())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .type(t.getType())
                .merchantName(t.getMerchantName())
                .status(t.getStatus())
                .transactionDate(t.getTransactionDate())
                .description(t.getDescription())
                .build();
    }

    public Transaction toEntity(TransactionDto dto, String accountNumber) {
        return Transaction.builder()
                .transactionReference(dto.getTransactionReference())
                .accountNumber(accountNumber)
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .type(dto.getType())
                .merchantName(dto.getMerchantName())
                .status(dto.getStatus())
                .transactionDate(dto.getTransactionDate())
                .description(dto.getDescription())
                .build();
    }
}
