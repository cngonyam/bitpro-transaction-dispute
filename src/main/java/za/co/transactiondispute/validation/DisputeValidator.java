package za.co.transactiondispute.validation;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.AccountStatus;
import za.co.transactiondispute.enums.DisputeReason;
import za.co.transactiondispute.enums.TransactionStatus;
import za.co.transactiondispute.enums.TransactionType;

import java.time.LocalDateTime;

@Component
public class DisputeValidator {

    public static final int DISPUTE_WINDOW_DAYS = 120;

    public void validate(DisputeCreateRequest request, Account account, Transaction transaction) {
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalArgumentException("Disputes cannot be raised on a closed account");
        }
        if (transaction.getType() != TransactionType.DEBIT) {
            throw new IllegalArgumentException("Only debit transactions can be disputed");
        }
        if (transaction.getStatus() != TransactionStatus.POSTED) {
            throw new IllegalArgumentException("Only posted transactions can be disputed");
        }
        if (transaction.getTransactionDate().isBefore(LocalDateTime.now().minusDays(DISPUTE_WINDOW_DAYS))) {
            throw new IllegalArgumentException("Transaction is older than the " + DISPUTE_WINDOW_DAYS + "-day dispute window");
        }
        if (request.getDisputedAmount().compareTo(transaction.getAmount()) > 0) {
            throw new IllegalArgumentException("Disputed amount cannot exceed the transaction amount");
        }
        if (request.getReasonCode() == DisputeReason.OTHER
                && (request.getDescription() == null || request.getDescription().isBlank())) {
            throw new IllegalArgumentException("A description is required when the reason is OTHER");
        }
    }
}
