package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import za.co.transactiondispute.controller.AccountController;
import za.co.transactiondispute.controller.DisputeController;
import za.co.transactiondispute.controller.TransactionController;
import za.co.transactiondispute.controller.UserProfileController;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.dto.ApproveDisputeRequest;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.ReverseDisputeRequest;
import za.co.transactiondispute.dto.ReviewDisputeRequest;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.dto.UserProfileDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.TransactionService;
import za.co.transactiondispute.service.UserProfileService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerFlowCoverageTest {

    @Mock
    private AccountService accountService;

    @Mock
    private DisputeService disputeService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private UserProfileService userProfileService;

    @Test
    void shouldLoadAccountsAndCreateNewAccountViaController() {
        AccountController accountController = new AccountController(accountService);
        Account account = Account.builder()
                .id(1L)
                .accountNumber("ACC-1001")
                .customerName("Jane Patel")
                .currency("ZAR")
                .availableBalance(new BigDecimal("250.00"))
                .balance(new BigDecimal("500.00"))
                .status("ACTIVE")
                .build();

        when(accountService.getAllAccounts()).thenReturn(List.of(account));
        when(accountService.getAccountById(1L)).thenReturn(Optional.of(account));
        when(accountService.createAccount(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Page<AccountDto> accounts = accountController.getAccounts(PageRequest.of(0, 10));
        assertEquals(1, accounts.getTotalElements());
        assertEquals("ACC-1001", accounts.getContent().get(0).getAccountNumber());

        ResponseEntity<AccountDto> accountById = accountController.getAccount(1L);
        assertEquals("Jane Patel", accountById.getBody().getCustomerName());

        AccountDto newAccount = new AccountDto();
        newAccount.setAccountNumber("ACC-2048");
        newAccount.setCustomerName("New Customer");
        newAccount.setCurrency("ZAR");
        newAccount.setAvailableBalance(new BigDecimal("100.00"));
        newAccount.setBalance(new BigDecimal("100.00"));
        newAccount.setStatus("ACTIVE");

        ResponseEntity<AccountDto> created = accountController.createAccount(newAccount);
        assertEquals(201, created.getStatusCodeValue());
        assertEquals("ACC-2048", created.getBody().getAccountNumber());
        assertTrue(created.getHeaders().getLocation().toString().contains("/api/v1/accounts/42"));
    }

    @Test
    void shouldLoadAndUpdateUserProfileViaController() {
        UserProfileController userProfileController = new UserProfileController(userProfileService);
        Principal principal = () -> "agent-user";
        UserProfile profile = UserProfile.builder()
                .id(5L)
                .username("agent-user")
                .fullName("Dev User")
                .role("USER")
                .email("dev@example.com")
                .phone("+27123456789")
                .department("Ops")
                .region("Johannesburg")
                .build();

        when(userProfileService.getProfile("agent-user")).thenReturn(profile);
        when(userProfileService.saveProfile(eq("agent-user"), any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile entity = invocation.getArgument(1);
            entity.setFullName("Updated User");
            entity.setRole("ADMIN");
            return entity;
        });

        ResponseEntity<UserProfileDto> currentProfile = userProfileController.getProfile(principal);
        assertEquals("Dev User", currentProfile.getBody().getFullName());

        UserProfileDto update = new UserProfileDto();
        update.setFullName("Updated User");
        update.setRole("ADMIN");
        update.setEmail("updated@example.com");
        update.setPhone("+27888888888");
        update.setDepartment("Finance");
        update.setRegion("Cape Town");

        ResponseEntity<UserProfileDto> savedProfile = userProfileController.updateProfile(update, principal);
        assertEquals("Updated User", savedProfile.getBody().getFullName());
        assertEquals("ADMIN", savedProfile.getBody().getRole());
    }

    @Test
    void shouldListAndReverseDisputesViaController() {
        DisputeController disputeController = new DisputeController(disputeService);
        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-001")
                .transactionReference("TX-100")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeService.getAllDisputes()).thenReturn(List.of(dispute));
        when(disputeService.getDisputeById(1L)).thenReturn(Optional.of(dispute));
        when(disputeService.createDispute(any(DisputeCreateRequest.class), anyString())).thenReturn(dispute);
        when(disputeService.reverseDispute(eq(1L), eq("REASON"), anyString())).thenReturn(dispute);

        Page<DisputeResponse> disputes = disputeController.getDisputes(PageRequest.of(0, 10));
        assertEquals(1, disputes.getTotalElements());
        assertEquals("DSP-001", disputes.getContent().get(0).getDisputeId());

        ResponseEntity<DisputeResponse> disputeById = disputeController.getDispute(1L);
        assertEquals("DSP-001", disputeById.getBody().getDisputeId());

        DisputeCreateRequest createRequest = new DisputeCreateRequest();
        createRequest.setTransactionReference("TX-100");
        createRequest.setAccountNumber("ACC-1001");
        createRequest.setDisputedAmount(new BigDecimal("25.00"));
        createRequest.setReasonCode("MERCHANT_ERROR");
        createRequest.setDisputeType(DisputeType.CHARGEBACK);
        createRequest.setDescription("Issue");

        ResponseEntity<?> createdDispute = disputeController.createDispute(createRequest);
        assertEquals(201, createdDispute.getStatusCodeValue());

        ReverseDisputeRequest reverseRequest = new ReverseDisputeRequest();
        reverseRequest.setReasonCode("REASON");

        ResponseEntity<?> reversed = disputeController.reverseDispute(1L, reverseRequest);
        assertEquals(200, reversed.getStatusCodeValue());
    }

    @Test
    void shouldReviewAndApproveDisputesViaController() {
        DisputeController disputeController = new DisputeController(disputeService);
        Dispute dispute = Dispute.builder()
                .id(2L)
                .disputeId("DSP-002")
                .transactionReference("TX-200")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("35.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeService.reviewDispute(2L, "APPROVE", "Needs verification")).thenReturn(dispute);
        when(disputeService.approveDispute(2L, "Approved by team lead")).thenReturn(dispute);

        ReviewDisputeRequest reviewRequest = new ReviewDisputeRequest();
        reviewRequest.setDecision("APPROVE");
        reviewRequest.setReviewNote("Needs verification");

        ResponseEntity<?> reviewed = disputeController.reviewDispute(2L, reviewRequest);
        assertEquals(200, reviewed.getStatusCodeValue());

        ApproveDisputeRequest approveRequest = new ApproveDisputeRequest();
        approveRequest.setApprovalNote("Approved by team lead");

        ResponseEntity<?> approved = disputeController.approveDispute(2L, approveRequest);
        assertEquals(200, approved.getStatusCodeValue());
    }

    @Test
    void shouldListAndCreateTransactionsViaController() {
        TransactionController transactionController = new TransactionController(transactionService, new TransactionMapper(), accountService);
        Transaction transaction = Transaction.builder()
                .id(1L)
                .transactionReference("TX-101")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("15.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Cafe")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Coffee")
                .build();

        when(transactionService.getAllTransactions()).thenReturn(List.of(transaction));
        when(transactionService.getTransactionByReference("TX-101")).thenReturn(Optional.of(transaction));
        when(accountService.getAccountByNumber("ACC-1001")).thenReturn(Optional.of(Account.builder()
                .accountNumber("ACC-1001")
                .availableBalance(new BigDecimal("200.00"))
                .balance(new BigDecimal("500.00"))
                .build()));
        when(transactionService.createTransaction(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setTransactionReference("TX-202");
            return saved;
        });

        Page<TransactionDto> txPage = transactionController.getTransactions(PageRequest.of(0, 10));
        assertEquals(1, txPage.getTotalElements());
        assertEquals("TX-101", txPage.getContent().get(0).getTransactionReference());

        ResponseEntity<TransactionDto> txByReference = transactionController.getTransaction("TX-101");
        assertEquals("TX-101", txByReference.getBody().getTransactionReference());

        TransactionDto dto = new TransactionDto();
        dto.setAccountNumber("ACC-1001");
        dto.setAmount(new BigDecimal("15.00"));
        dto.setCurrency("ZAR");
        dto.setType(TransactionType.DEBIT);
        dto.setMerchantName("Cafe");
        dto.setStatus("POSTED");
        dto.setDescription("Coffee");

        ResponseEntity<TransactionDto> createdTransaction = transactionController.createTransaction(dto);
        assertEquals(201, createdTransaction.getStatusCodeValue());
        assertEquals("TX-202", createdTransaction.getBody().getTransactionReference());
    }
}
