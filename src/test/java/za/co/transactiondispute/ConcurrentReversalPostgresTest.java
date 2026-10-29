package za.co.transactiondispute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs the reversal money flow against a real PostgreSQL instance, where
 * {@code SELECT ... FOR UPDATE} is honoured. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentReversalPostgresTest {

    private static final String ACCOUNT = "ACC-1001";
    private static final int THREADS = 8;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentReversalsWithDistinctKeysCreditExactlyOnce() throws Exception {
        long disputeId = approvedDispute();

        List<Integer> statuses = fireConcurrently(disputeId, i -> UUID.randomUUID().toString());

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "exactly one reversal may succeed: " + statuses);
        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 409), "losers must get 409: " + statuses);
        assertSingleCredit(disputeId);
    }

    @Test
    void concurrentReversalsWithSameKeyCreditExactlyOnce() throws Exception {
        long disputeId = approvedDispute();
        String key = UUID.randomUUID().toString();

        List<Integer> statuses = fireConcurrently(disputeId, i -> key);

        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 409), "unexpected statuses: " + statuses);
        assertTrue(statuses.contains(200), "one request must win: " + statuses);
        assertSingleCredit(disputeId);
    }

    @Test
    void auditTablesRejectUpdatesAndDeletes() throws Exception {
        approvedDispute();

        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE dispute_status_history SET actor = 'tamper'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM dispute_status_history"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE dispute_ledger_entries SET actor = 'tamper'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM dispute_ledger_entries"));
    }

    private void assertSingleCredit(long disputeId) {
        Integer credits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE related_dispute_id = ? AND type = 'CREDIT'",
                Integer.class, disputeId);
        Integer ledgerReversals = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dispute_ledger_entries WHERE dispute_id = ? AND action = 'REVERSED'",
                Integer.class, disputeId);
        assertEquals(1, credits, "exactly one credit transaction expected");
        assertEquals(1, ledgerReversals, "exactly one REVERSED ledger entry expected");
    }

    private List<Integer> fireConcurrently(long disputeId, java.util.function.IntFunction<String> keyFor) throws Exception {
        String agent = login("agent-user", "agent-pass123");
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                String key = keyFor.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/api/v1/disputes/{id}/reverse", disputeId)
                                    .header("Authorization", "Bearer " + agent)
                                    .header("X-Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"reasonCode\":\"FRAUD\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long approvedDispute() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        String agent = login("agent-user", "agent-pass123");
        String lead = login("lead-user", "lead-pass123");

        String reference = "TXN-PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        transactionRepository.save(Transaction.builder()
                .transactionReference(reference)
                .accountNumber(ACCOUNT)
                .amount(new BigDecimal("120.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Concurrency Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("concurrency test")
                .build());

        String created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + customer)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"" + reference + "\",\"accountNumber\":\"" + ACCOUNT + "\","
                                + "\"disputedAmount\":10.00,\"reasonCode\":\"MERCHANT_ERROR\",\"disputeType\":\"CHARGEBACK\","
                                + "\"description\":\"Charged incorrectly\"}"))
                .andReturn().getResponse().getContentAsString();
        long disputeId = objectMapper.readTree(created).get("id").asLong();

        assertEquals(200, mockMvc.perform(post("/api/v1/disputes/{id}/review", disputeId)
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALID_CLAIM\",\"reviewNote\":\"Looks legitimate\"}"))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(post("/api/v1/disputes/{id}/approve", disputeId)
                        .header("Authorization", "Bearer " + lead)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNote\":\"Evidence verified\"}"))
                .andReturn().getResponse().getStatus());
        return disputeId;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
