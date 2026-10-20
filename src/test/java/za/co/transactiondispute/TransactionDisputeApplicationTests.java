package za.co.transactiondispute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.stream.StreamSupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionDisputeApplicationTests {

    private static final String ACCOUNT = "1052348976";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private static RequestPostProcessor token(String subject, String role) {
        return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void devTokenIsAcceptedByTheApi() throws Exception {
        String body = mvc.perform(post("/dev/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"thabo\",\"roles\":[\"customer\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = mapper.readTree(body).get("access_token").asText();

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value(ACCOUNT));
    }

    @Test
    void customerCannotSeeAnotherCustomersAccount() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + ACCOUNT).with(token("lerato", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCannotUpdateDisputeStatus() throws Exception {
        mvc.perform(patch("/api/v1/disputes/DSP-X/status").with(token("thabo", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullDisputeLifecycle() throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/" + ACCOUNT + "/transactions?size=100").with(token("thabo", "CUSTOMER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode debit = StreamSupport.stream(mapper.readTree(body).get("content").spliterator(), false)
                .filter(t -> "DEBIT".equals(t.get("type").asText()) && "POSTED".equals(t.get("status").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mock feed returned no disputable transaction"));
        String reference = debit.get("transactionReference").asText();
        String amount = debit.get("amount").asText();

        String request = "{\"transactionReference\":\"" + reference + "\",\"accountNumber\":\"" + ACCOUNT
                + "\",\"disputedAmount\":" + amount + ",\"reasonCode\":\"UNAUTHORISED_TRANSACTION\"}";

        String created = mvc.perform(post("/api/v1/disputes").with(token("thabo", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String disputeId = mapper.readTree(created).get("disputeId").asText();

        mvc.perform(post("/api/v1/disputes").with(token("thabo", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/disputes/" + disputeId).with(token("lerato", "CUSTOMER")))
                .andExpect(status().isNotFound());

        String statusUrl = "/api/v1/disputes/" + disputeId + "/status";
        mvc.perform(patch(statusUrl).with(token("agent-1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mvc.perform(patch(statusUrl).with(token("agent-1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"note\":\"Refund approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(get("/api/v1/disputes/" + disputeId).with(token("thabo", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3));
    }
}
