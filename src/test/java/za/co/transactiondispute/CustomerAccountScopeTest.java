package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.impl.TransactionServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccountScopeTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CustomerAccountContext customerAccountContext;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    void shouldReturnOnlyTheCurrentCustomerTransactions() {
        Transaction allowed = new Transaction();
        allowed.setAccountNumber("ACC-1001");
        allowed.setTransactionReference("TX-100");

        Transaction blocked = new Transaction();
        blocked.setAccountNumber("ACC-1002");
        blocked.setTransactionReference("TX-200");

        when(customerAccountContext.getCurrentAccountNumber()).thenReturn("ACC-1001");
        when(transactionRepository.findByAccountNumber("ACC-1001"))
                .thenReturn(List.of(allowed));

        List<Transaction> customerTransactions = transactionService.getAllTransactions();

        assertEquals(1, customerTransactions.size());
        assertEquals("ACC-1001", customerTransactions.get(0).getAccountNumber());
    }
}
