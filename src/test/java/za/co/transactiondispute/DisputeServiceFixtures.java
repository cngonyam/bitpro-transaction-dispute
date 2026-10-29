package za.co.transactiondispute;

import org.mockito.Mockito;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.entity.DisputeIdempotency;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeIdempotencyRepository;
import za.co.transactiondispute.repository.DisputeLedgerEntryRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.DisputeStatusHistoryRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.UserProfileService;
import za.co.transactiondispute.service.impl.DisputeServiceImpl;
import za.co.transactiondispute.validation.DisputeValidator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.withSettings;

/**
 * Builds a {@link DisputeServiceImpl} for unit tests. Collaborators that a test does not care about
 * are replaced with lenient mocks, and the idempotency repository behaves like a real store.
 */
final class DisputeServiceFixtures {

    private DisputeServiceFixtures() {
    }

    static DisputeServiceImpl build(DisputeRepository disputeRepository,
                                    TransactionRepository transactionRepository,
                                    DisputeValidator validator,
                                    EmailClient emailClient,
                                    SmsClient smsClient,
                                    CustomerAccountContext context,
                                    AccountRepository accountRepository,
                                    UserProfileService userProfileService) {
        return build(disputeRepository, transactionRepository, validator, emailClient, smsClient, context,
                accountRepository, userProfileService, null, null, null);
    }

    static DisputeServiceImpl build(DisputeRepository disputeRepository,
                                    TransactionRepository transactionRepository,
                                    DisputeValidator validator,
                                    EmailClient emailClient,
                                    SmsClient smsClient,
                                    CustomerAccountContext context,
                                    AccountRepository accountRepository,
                                    UserProfileService userProfileService,
                                    DisputeStatusHistoryRepository historyRepository,
                                    DisputeIdempotencyRepository idempotencyRepository) {
        return build(disputeRepository, transactionRepository, validator, emailClient, smsClient, context,
                accountRepository, userProfileService, historyRepository, idempotencyRepository, null);
    }

    static DisputeServiceImpl build(DisputeRepository disputeRepository,
                                    TransactionRepository transactionRepository,
                                    DisputeValidator validator,
                                    EmailClient emailClient,
                                    SmsClient smsClient,
                                    CustomerAccountContext context,
                                    AccountRepository accountRepository,
                                    UserProfileService userProfileService,
                                    DisputeStatusHistoryRepository historyRepository,
                                    DisputeIdempotencyRepository idempotencyRepository,
                                    DisputeLedgerEntryRepository ledgerEntryRepository) {
        return new DisputeServiceImpl(disputeRepository, transactionRepository, validator, emailClient, smsClient,
                context, accountRepository, userProfileService,
                historyRepository != null ? historyRepository : lenientMock(DisputeStatusHistoryRepository.class),
                idempotencyRepository != null ? idempotencyRepository : inMemoryIdempotencyRepository(),
                ledgerEntryRepository != null ? ledgerEntryRepository : lenientMock(DisputeLedgerEntryRepository.class));
    }

    private static <T> T lenientMock(Class<T> type) {
        return Mockito.mock(type, withSettings().lenient());
    }

    private static DisputeIdempotencyRepository inMemoryIdempotencyRepository() {
        DisputeIdempotencyRepository repository = lenientMock(DisputeIdempotencyRepository.class);
        Map<String, DisputeIdempotency> store = new ConcurrentHashMap<>();
        Mockito.lenient().when(repository.findByUsernameAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))));
        Mockito.lenient().when(repository.save(any(DisputeIdempotency.class))).thenAnswer(invocation -> {
            DisputeIdempotency record = invocation.getArgument(0);
            store.put(key(record.getUsername(), record.getOperation(), record.getIdempotencyKey()), record);
            return record;
        });
        return repository;
    }

    private static String key(String username, String operation, String idempotencyKey) {
        return username + ":" + operation + ":" + idempotencyKey;
    }
}
