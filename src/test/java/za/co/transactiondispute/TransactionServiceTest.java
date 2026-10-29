package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.impl.TransactionServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Test
    void shouldCreateDebitTransactionWithGeneratedReferenceAndReconciledBalance() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        TransactionServiceImpl service = new TransactionServiceImpl(transactionRepository, context, accountRepository);

        Account account = Account.builder()
                .id(10L)
                .accountNumber("ACC-1001")
                .customerName("Jane Patel")
                .currency("ZAR")
                .availableBalance(new BigDecimal("500.00"))
                .balance(new BigDecimal("500.00"))
                .status("ACTIVE")
                .build();

        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction request = Transaction.builder()
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("15.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Cafe")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Coffee")
                .build();

        Transaction saved = service.createTransaction(request);

        assertNotNull(saved.getTransactionReference());
        assertTrue(saved.getTransactionReference().startsWith("TX-"));
        assertEquals(new BigDecimal("-15.00"), saved.getAmount());
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
        verify(accountRepository, atLeastOnce()).save(any(Account.class));
    }

    @Test
    void shouldRejectNullOrUnsupportedTransactionPayloads() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        TransactionServiceImpl service = new TransactionServiceImpl(transactionRepository, context, accountRepository);

        assertThrows(IllegalArgumentException.class, () -> service.createTransaction(null));
        assertThrows(IllegalArgumentException.class, () -> service.createTransaction(Transaction.builder()
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("10.00"))
                .build()));
        assertThrows(IllegalArgumentException.class, () -> service.createTransaction(Transaction.builder()
                .accountNumber("ACC-1001")
                .type(TransactionType.CREDIT)
                .amount(new BigDecimal("10.00"))
                .build()));
        assertThrows(IllegalArgumentException.class, () -> service.createTransaction(Transaction.builder()
                .accountNumber("ACC-999")
                .type(TransactionType.DEBIT)
                .amount(new BigDecimal("10.00"))
                .build()));
    }

    @Test
    void shouldReturnCurrentCustomerTransactionByReference() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        TransactionServiceImpl service = new TransactionServiceImpl(transactionRepository, context, accountRepository);

        Transaction transaction = Transaction.builder()
                .transactionReference("TX-100")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Original")
                .build();

        when(transactionRepository.findByTransactionReference("TX-100")).thenReturn(Optional.of(transaction));

        assertEquals("TX-100", service.getTransactionByReference("TX-100").orElseThrow().getTransactionReference());
    }

    @Test
    void shouldRejectTransactionsOutsideCurrentCustomerScope() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        TransactionServiceImpl service = new TransactionServiceImpl(transactionRepository, context, accountRepository);

        when(transactionRepository.findByTransactionReference("TX-999")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-999")
                .accountNumber("ACC-999")
                .amount(new BigDecimal("30.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Other")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Other account")
                .build()));

        assertThrows(ResourceNotFoundException.class, () -> service.getTransactionByReference("TX-999"));
    }
}
