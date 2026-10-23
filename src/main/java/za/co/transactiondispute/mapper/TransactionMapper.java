package za.co.transactiondispute.mapper;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    public TransactionDto toDto(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionDto.builder()
                .id(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .accountNumber(transaction.getAccountNumber())
                .accountBalance(null)
                .amount(normalizeAmount(transaction.getType(), transaction.getAmount()))
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .merchantName(transaction.getMerchantName())
                .status(transaction.getStatus())
                .transactionDate(transaction.getTransactionDate())
                .description(transaction.getDescription())
                .build();
    }

    public Transaction toEntity(TransactionDto dto) {
        if (dto == null) {
            return null;
        }

        return Transaction.builder()
                .id(dto.getId())
                .transactionReference(dto.getTransactionReference())
                .accountNumber(dto.getAccountNumber())
                .amount(normalizeAmount(dto.getType(), dto.getAmount()))
                .currency(dto.getCurrency())
                .type(dto.getType())
                .merchantName(dto.getMerchantName())
                .status(dto.getStatus())
                .transactionDate(dto.getTransactionDate())
                .description(dto.getDescription())
                .build();
    }

    private BigDecimal normalizeAmount(TransactionType type, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal magnitude = amount.abs();
        return type == TransactionType.DEBIT ? magnitude.negate() : magnitude;
    }
}
