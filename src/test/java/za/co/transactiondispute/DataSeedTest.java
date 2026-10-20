package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.TransactionRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DataSeedTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    @Test
    void shouldSeedSampleDataOnStartup() {
        assertTrue(accountRepository.count() > 0);
        assertTrue(transactionRepository.count() > 0);
        assertTrue(disputeRepository.count() > 0);
        assertFalse(disputeRepository.findAll().stream()
                .anyMatch(dispute -> dispute.getCreatedAt() == null || dispute.getUpdatedAt() == null));
    }
}
