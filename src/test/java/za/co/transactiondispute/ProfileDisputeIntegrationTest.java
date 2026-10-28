package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.UserProfileService;

import java.time.LocalDateTime;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ProfileDisputeIntegrationTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldAttachCurrentProfileToNewDispute() {
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("agent-user", "agent-pass123"));

            UserProfile profile = UserProfile.builder()
                    .username("agent-user")
                    .fullName("Ndumiso Ngonyama")
                    .role("Dispute Operations Lead")
                    .email("ndumiso.ngonyama@bitpro.co.za")
                    .phone("+27 11 555 0123")
                    .department("Customer Operations")
                    .region("Johannesburg, South Africa")
                    .build();

            userProfileService.saveProfile("agent-user", profile);

            transactionRepository.save(Transaction.builder()
                    .transactionReference("TXN-10001")
                    .accountNumber("ACC-1001")
                    .amount(new BigDecimal("99.00"))
                    .currency("ZAR")
                    .type(TransactionType.DEBIT)
                    .merchantName("Example Merchant")
                    .status("POSTED")
                    .transactionDate(LocalDateTime.now())
                    .description("Existing customer transaction")
                    .build());

            DisputeCreateRequest request = new DisputeCreateRequest();
            request.setTransactionReference("TXN-10001");
            request.setAccountNumber("ACC-1001");
            request.setDisputedAmount(new BigDecimal("99.00"));
            request.setReasonCode("MERCHANT_ERROR");
            request.setDescription("Profile linked dispute");
            request.setDisputeType(null);

            var dispute = disputeService.createDispute(request);

            assertNotNull(dispute);
            assertEquals("Ndumiso Ngonyama", dispute.getAssignedToFullName());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
