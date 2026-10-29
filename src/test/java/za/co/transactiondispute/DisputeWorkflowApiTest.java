package za.co.transactiondispute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end money-flow tests over HTTP: authentication, role checks, the
 * review -> approve -> reverse lifecycle, idempotency and conflict handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisputeWorkflowApiTest {

    private static final String ACCOUNT = "ACC-1001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void customerCannotReviewApproveOrReverse() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        long disputeId = createDispute(customer, newTransaction(TransactionType.DEBIT, "40.00"));

        mockMvc.perform(post("/api/v1/disputes/{id}/review", disputeId)
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALID\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/disputes/{id}/approve", disputeId)
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNote\":\"ok\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/disputes/{id}/reverse", disputeId)
                        .header("Authorization", bearer(customer))
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FRAUD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void creditTransactionsCannotBeDisputed() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        String reference = newTransaction(TransactionType.CREDIT, "40.00");

        createDisputeRequest(customer, reference, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("debit")));
    }

    @Test
    void createRequiresIdempotencyKeyAndReplaysSafely() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        String reference = newTransaction(TransactionType.DEBIT, "10.00");

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(reference)))
                .andExpect(status().isBadRequest());

        String key = UUID.randomUUID().toString();
        long first = idOf(createDisputeRequest(customer, reference, key).andExpect(status().isCreated()));
        long replay = idOf(createDisputeRequest(customer, reference, key).andExpect(status().isCreated()));
        assertEquals(first, replay);
    }

    @Test
    void approvalIsRestrictedToLeadAndAdmin() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        String agent = login("agent-user", "agent-pass123");
        long disputeId = createDispute(customer, newTransaction(TransactionType.DEBIT, "55.00"));

        review(agent, disputeId).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/disputes/{id}/approve", disputeId)
                        .header("Authorization", bearer(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNote\":\"agents cannot approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reversalMustFollowApprovalAndCanOnlyBePostedOnce() throws Exception {
        String customer = login("customer-user", "customer-pass123");
        String agent = login("agent-user", "agent-pass123");
        String lead = login("lead-user", "lead-pass123");
        long disputeId = createDispute(customer, newTransaction(TransactionType.DEBIT, "75.00"));

        // Not yet approved -> reversal rejected
        reverse(agent, disputeId, "FRAUD", UUID.randomUUID().toString()).andExpect(status().isBadRequest());

        review(agent, disputeId).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/disputes/{id}/approve", disputeId)
                        .header("Authorization", bearer(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNote\":\"Evidence verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVISIONAL_CREDIT_ISSUED"));

        String key = UUID.randomUUID().toString();
        reverse(agent, disputeId, "FRAUD", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.decisionOutcome").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("FRAUD"));

        // Same key + same payload: replay returns the original result without a second credit
        reverse(agent, disputeId, "FRAUD", key).andExpect(status().isOk());

        // Same key + different payload: rejected
        reverse(agent, disputeId, "DUPLICATE", key).andExpect(status().isBadRequest());

        // New key on an already-reversed dispute: conflict
        reverse(agent, disputeId, "FRAUD", UUID.randomUUID().toString()).andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/disputes/{id}/history", disputeId)
                        .header("Authorization", bearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    private ResultActions review(String jwt, long disputeId) throws Exception {
        return mockMvc.perform(post("/api/v1/disputes/{id}/review", disputeId)
                .header("Authorization", bearer(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VALID_CLAIM\",\"reviewNote\":\"Looks legitimate\"}"));
    }

    private ResultActions reverse(String jwt, long disputeId, String reason, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/disputes/{id}/reverse", disputeId)
                .header("Authorization", bearer(jwt))
                .header("X-Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"" + reason + "\"}"));
    }

    private long createDispute(String jwt, String reference) throws Exception {
        return idOf(createDisputeRequest(jwt, reference, UUID.randomUUID().toString()).andExpect(status().isCreated()));
    }

    private ResultActions createDisputeRequest(String jwt, String reference, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/disputes")
                .header("Authorization", bearer(jwt))
                .header("X-Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(reference)));
    }

    private String createBody(String reference) {
        return "{\"transactionReference\":\"" + reference + "\",\"accountNumber\":\"" + ACCOUNT + "\","
                + "\"disputedAmount\":10.00,\"reasonCode\":\"MERCHANT_ERROR\",\"disputeType\":\"CHARGEBACK\",\"description\":\"Charged incorrectly\"}";
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String newTransaction(TransactionType type, String amount) {
        String reference = "TXN-WF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        transactionRepository.save(Transaction.builder()
                .transactionReference(reference)
                .accountNumber(ACCOUNT)
                .amount(new BigDecimal(amount))
                .currency("ZAR")
                .type(type)
                .merchantName("Workflow Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("workflow test")
                .build());
        return reference;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String bearer(String jwt) {
        return "Bearer " + jwt;
    }
}
