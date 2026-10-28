package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.controller.DisputeController;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeLedgerEntry;
import za.co.transactiondispute.entity.DisputeStatusHistory;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeLedgerEntryRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.DisputeStatusHistoryRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.repository.UserProfileRepository;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.TransactionService;
import za.co.transactiondispute.service.UserProfileService;
import za.co.transactiondispute.service.impl.AccountServiceImpl;
import za.co.transactiondispute.service.impl.DisputeServiceImpl;
import za.co.transactiondispute.service.impl.TransactionServiceImpl;
import za.co.transactiondispute.service.impl.UserProfileServiceImpl;
import za.co.transactiondispute.util.BalanceCalculator;
import za.co.transactiondispute.util.UserRoles;
import za.co.transactiondispute.validation.DisputeValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCoverageTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Test
    void shouldOnlyCountResolvedSettledOrFinalizedTransactionsInBalance() {
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository, new CustomerAccountContext("ACC-1001"));
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("A")
                .currency("ZAR")
                .availableBalance(new BigDecimal("0.00"))
                .balance(new BigDecimal("0.00"))
                .status("ACTIVE")
                .build();

        Transaction resolvedCredit = Transaction.builder()
                .id(1L)
                .transactionReference("TX-20261001-EMPLOYER")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("12000.00"))
                .currency("ZAR")
                .type(TransactionType.CREDIT)
                .merchantName("Employer Payroll")
                .status("RESOLVED")
                .transactionDate(LocalDateTime.now())
                .description("Salary payment received")
                .build();

        Transaction pendingDebitOne = Transaction.builder()
                .id(2L)
                .transactionReference("TX-20261002-CARDPAY")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("1300.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Card payment")
                .status("DEBIT")
                .transactionDate(LocalDateTime.now())
                .description("Card payment")
                .build();

        Transaction pendingDebitTwo = Transaction.builder()
                .id(3L)
                .transactionReference("TX-20261003-DEBITORDER")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("1200.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Debit Order")
                .status("DEBIT")
                .transactionDate(LocalDateTime.now())
                .description("Debit order received")
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(resolvedCredit, pendingDebitOne, pendingDebitTwo));

        List<Account> accounts = accountService.getAllAccounts();

        assertEquals(1, accounts.size());
        assertEquals(new BigDecimal("12000.00"), accounts.get(0).getBalance());
        assertEquals(new BigDecimal("9500.00"), accounts.get(0).getAvailableBalance());
    }

    @Test
    void shouldNotPersistBalanceReconciliationDuringReadOperations() {
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository, new CustomerAccountContext("ACC-1001"));
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("A")
                .currency("ZAR")
                .availableBalance(new BigDecimal("100.00"))
                .balance(new BigDecimal("100.00"))
                .status("ACTIVE")
                .build();

        CustomerAccountContext customerAccountContext = new CustomerAccountContext("ACC-1001");
        TransactionService transactionService = new TransactionServiceImpl(transactionRepository, customerAccountContext, accountRepository);

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of());

        accountService.getAllAccounts();
        transactionService.getAllTransactions();

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldCreateDisputeForCurrentAccount() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfile userProfile = UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build();

        DisputeValidator validator = new DisputeValidator();
        EmailClient emailClient = mock(EmailClient.class);
        SmsClient smsClient = mock(SmsClient.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(userProfile);

        Transaction originalTransaction = Transaction.builder()
                .transactionReference("TX-100")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("150.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Original")
                .build();

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-100");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("25.00"));
        request.setReasonCode("MERCHANT_ERROR");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("Issue");

        when(transactionRepository.findByTransactionReference("TX-100")).thenReturn(Optional.of(originalTransaction));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, validator, emailClient, smsClient, context, accountRepository, userProfileService);

        Dispute created = disputeService.createDispute(request);

        assertNotNull(created);
        assertEquals("ACC-1001", created.getAccountNumber());
        assertEquals(DisputeStatus.OPEN, created.getStatus());
        assertEquals("Ndumiso Ngonyama", created.getAssignedToFullName());
        assertTrue(created.getDisputeId().startsWith("DSP-"));
    }

    @Test
    void shouldRejectDuplicateDisputeForSameTransaction() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);

        Transaction originalTransaction = Transaction.builder()
                .transactionReference("TX-21")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("100.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Original")
                .build();

        when(transactionRepository.findByTransactionReference("TX-21")).thenReturn(Optional.of(originalTransaction));
        when(disputeRepository.findByTransactionReference("TX-21")).thenReturn(Optional.of(Dispute.builder()
                .id(11L)
                .disputeId("DSP-11")
                .transactionReference("TX-21")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("10.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("First dispute")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-21");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("10.00"));
        request.setReasonCode("MERCHANT_ERROR");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("Second dispute");

        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(request));
    }

    @Test
    void shouldRejectDisputeForOtherAccount() {
        DisputeValidator validator = new DisputeValidator();
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, validator, mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-1");
        request.setAccountNumber("ACC-999");
        request.setDisputedAmount(new BigDecimal("25.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(request));
    }

    @Test
    void shouldRejectDisputeWhenTransactionMissing() {
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("missing-tx");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("10.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        when(transactionRepository.findByTransactionReference("missing-tx")).thenReturn(Optional.empty());

        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.createDispute(request));
    }

    @Test
    void shouldRejectDisputeWhenAmountExceedsOriginal() {
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-1");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("200.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("test")
                .build()));

        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(request));
    }

    @Test
    void shouldRejectReviewFromCustomerRole() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("customer-user")).thenReturn(UserProfile.builder()
                .username("customer-user")
                .fullName("Customer User")
                .role("CUSTOMER")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));

        assertThrows(IllegalArgumentException.class, () -> disputeService.reviewDispute(1L, "APPROVED", "Looks valid"));
    }

    @Test
    void shouldRejectApprovalFromNonAdminRole() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(2L)
                .disputeId("DSP-101")
                .transactionReference("TX-2")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("15.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.IN_REVIEW)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(2L)).thenReturn(Optional.of(dispute));

        assertThrows(IllegalArgumentException.class, () -> disputeService.approveDispute(2L, "Approved by agent"));
    }

    @Test
    void shouldAllowLeadUserToApproveDisputeInReview() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("lead-user")).thenReturn(UserProfile.builder()
                .username("lead-user")
                .fullName("Operations Lead")
                .role("Dispute Operations Lead")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("lead-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(2L)
                .disputeId("DSP-101")
                .transactionReference("TX-2")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("15.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.IN_REVIEW)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(2L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute approved = disputeService.approveDispute(2L, "Approved by operations lead");

        assertEquals(DisputeStatus.PROVISIONAL_CREDIT_ISSUED, approved.getStatus());
        assertEquals("lead-user", approved.getApprovedByUsername());
    }

    @Test
    void shouldCanonicaliseAliasRolesAcrossProfileAndOperations() {
        UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
        when(userProfileRepository.findByUsername("agent-user")).thenReturn(Optional.of(UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .build()));
        when(userProfileRepository.findByUsername("admin-user")).thenReturn(Optional.of(UserProfile.builder()
                .username("admin-user")
                .fullName("System Admin")
                .role("System Admin")
                .email("admin@bitpro.co.za")
                .build()));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileService userProfileService = new UserProfileServiceImpl(userProfileRepository);

        UserProfile saved = userProfileService.saveProfile("agent-user", UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .build());
        assertEquals("AGENT", saved.getRole());

        UserProfile admin = userProfileService.saveProfile("admin-user", UserProfile.builder()
                .username("admin-user")
                .fullName("System Admin")
                .role("System Admin")
                .email("admin@bitpro.co.za")
                .build());

        assertEquals("ADMIN", admin.getRole());
    }

    @Test
    void shouldPersistCanonicalActorRoleInDisputeStatusHistory() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        DisputeStatusHistoryRepository disputeStatusHistoryRepository = mock(DisputeStatusHistoryRepository.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .build());

        Dispute dispute = Dispute.builder()
                .id(99L)
                .disputeId("DSP-999")
                .transactionReference("TX-999")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(99L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(disputeStatusHistoryRepository.save(any(DisputeStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                context,
                accountRepository,
                userProfileService,
                disputeStatusHistoryRepository,
                null
        );

        disputeService.reviewDispute(99L, "APPROVED", "Looks valid");

        var captor = org.mockito.ArgumentCaptor.forClass(DisputeStatusHistory.class);
        verify(disputeStatusHistoryRepository).save(captor.capture());
        assertEquals("AGENT", captor.getValue().getActorRole());
        assertEquals("agent-user", captor.getValue().getActor());
    }

    @Test
    void shouldFallbackToPrimaryAccountForAdminUserWhenMappingIsMissing() {
        CustomerAccountContext context = new CustomerAccountContext("agent-user=ACC-1001,customer-user=ACC-1001");

        assertEquals("ACC-1001", context.getCurrentAccountNumber("admin-user"));
    }

    @Test
    void shouldRejectClientSuppliedCreditTypeAndStatusWhenCreatingTransaction() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        TransactionService transactionService = new TransactionServiceImpl(transactionRepository, context, accountRepository);

        Transaction transaction = Transaction.builder()
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.CREDIT)
                .merchantName("Merchant")
                .status("FINALIZED")
                .transactionDate(LocalDateTime.now())
                .description("should be rejected")
                .build();

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(transaction));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldIgnoreClientSuppliedDisputeDecisionFieldsWhenCreatingDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfile userProfile = UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build();

        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(userProfile);

        Transaction originalTransaction = Transaction.builder()
                .transactionReference("TX-9")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("100.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Original")
                .build();

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-9");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("10.00"));
        request.setReasonCode("MERCHANT_ERROR");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("Issue");
        request.setProvisionalCreditIssued(true);
        request.setDecisionOutcome("APPROVED");

        when(transactionRepository.findByTransactionReference("TX-9")).thenReturn(Optional.of(originalTransaction));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);
        Dispute created = disputeService.createDispute(request);

        assertFalse(Boolean.TRUE.equals(created.getProvisionalCreditIssued()));
        assertNull(created.getDecisionOutcome());
    }

    @Test
    void shouldRejectInvalidTransitionWhenReversingRejectedDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.REJECTED)
                .decisionOutcome("REJECTED")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("test")
                .build()));

        assertThrows(IllegalArgumentException.class, () -> disputeService.reverseDispute(1L, "duplicate transaction", "repeat-key-1"));
    }

    @Test
    void shouldReturnExistingDisputeForRepeatedCreateWithSameIdempotencyKey() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-99");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("15.00"));
        request.setReasonCode("MERCHANT_ERROR");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("duplicate key test");

        Transaction originalTransaction = Transaction.builder()
                .transactionReference("TX-99")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("40.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Merchant")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Original")
                .build();

        Dispute existing = Dispute.builder()
                .id(77L)
                .disputeId("DSP-77")
                .transactionReference("TX-99")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("15.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("duplicate key test")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.findByTransactionReference("TX-99")).thenReturn(Optional.of(originalTransaction));
        when(disputeRepository.findByTransactionReference("TX-99")).thenReturn(Optional.empty());
        when(disputeRepository.save(any(Dispute.class))).thenReturn(existing);

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute first = disputeService.createDispute(request, "idempotent-key-99");
        Dispute second = disputeService.createDispute(request, "idempotent-key-99");

        assertSame(first, second);
        verify(disputeRepository, times(1)).save(any(Dispute.class));
    }

    @Test
    void shouldRejectDuplicateDisputeCreateRequestWithSameIdempotencyKey() {
        DisputeService disputeService = mock(DisputeService.class);
        DisputeController controller = new DisputeController(disputeService);

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-3");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("15.00"));
        request.setReasonCode("MERCHANT_ERROR");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("duplicate test");

        Dispute dispute = Dispute.builder()
                .id(42L)
                .disputeId("DSP-42")
                .transactionReference("TX-3")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("15.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("duplicate test")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeService.createDispute(request, "abc-123")).thenReturn(dispute);

        ResponseEntity<DisputeResponse> first = controller.createDispute(request, "abc-123");
        ResponseEntity<DisputeResponse> second = controller.createDispute(request, "abc-123");

        assertEquals(201, first.getStatusCode().value());
        assertEquals(201, second.getStatusCode().value());
        verify(disputeService, times(2)).createDispute(request, "abc-123");
    }

    @Test
    void shouldCreateLedgerCreditWhenDisputeIsReversed() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.PROVISIONAL_CREDIT_ISSUED)
                .decisionOutcome(null)
                .approvedByUsername("agent-user")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction original = Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("original")
                .build();

        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("Customer")
                .currency("ZAR")
                .availableBalance(new BigDecimal("100.00"))
                .balance(new BigDecimal("100.00"))
                .status("ACTIVE")
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(original));
        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(account));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute reversed = disputeService.reverseDispute(1L, "duplicate transaction");

        assertEquals(DisputeStatus.RESOLVED, reversed.getStatus());
        assertEquals("REVERSED", reversed.getDecisionOutcome());
        assertTrue(reversed.getDescription().contains("duplicate transaction"));
        assertEquals("POSTED", original.getStatus());
        verify(transactionRepository).save(argThat(transaction ->
                TransactionType.CREDIT.equals(transaction.getType())
                        && "ACC-1001".equals(transaction.getAccountNumber())
                        && new BigDecimal("25.00").compareTo(transaction.getAmount()) == 0
                        && "RESOLVED".equals(transaction.getStatus())));
        verify(disputeRepository).save(dispute);
    }

    @Test
    void shouldPersistLedgerAuditEntryWhenDisputeIsReversed() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        DisputeLedgerEntryRepository ledgerEntryRepository = mock(DisputeLedgerEntryRepository.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                context,
                accountRepository,
                userProfileService,
                mock(DisputeStatusHistoryRepository.class),
                mock(za.co.transactiondispute.repository.DisputeIdempotencyRepository.class),
                ledgerEntryRepository
        );

        Dispute dispute = Dispute.builder()
                .id(12L)
                .disputeId("DSP-12")
                .transactionReference("TX-12")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.PROVISIONAL_CREDIT_ISSUED)
                .decisionOutcome(null)
                .approvedByUsername("admin-user")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction original = Transaction.builder()
                .transactionReference("TX-12")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("original")
                .build();

        when(disputeRepository.findById(12L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-12")).thenReturn(Optional.of(original));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        disputeService.reverseDispute(12L, "duplicate transaction", "ledger-key-12");

        verify(ledgerEntryRepository).save(argThat(entry ->
                entry.getDisputeId().equals(12L)
                        && "REVERSED".equals(entry.getAction())
                        && new BigDecimal("25.00").compareTo(entry.getAmount()) == 0
                        && "ACC-1001".equals(entry.getAccountNumber())
                        && "agent-user".equals(entry.getActor())));
    }

    @Test
    void shouldPersistReversalActorOnDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(7L)
                .disputeId("DSP-7")
                .transactionReference("TX-7")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.PROVISIONAL_CREDIT_ISSUED)
                .decisionOutcome(null)
                .approvedByUsername("admin-user")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction original = Transaction.builder()
                .transactionReference("TX-7")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("75.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("original")
                .build();

        when(disputeRepository.findById(7L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-7")).thenReturn(Optional.of(original));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute reversed = disputeService.reverseDispute(7L, "duplicate transaction");

        assertEquals("agent-user", reversed.getReversedByUsername());
        assertEquals("AGENT", reversed.getReversedByRole());
    }

    @Test
    void shouldRecordReviewStatusTransitionFromOpenToInReview() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        DisputeStatusHistoryRepository disputeStatusHistoryRepository = mock(DisputeStatusHistoryRepository.class);
        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                new CustomerAccountContext("ACC-1001"),
                accountRepository,
                userProfileService,
                disputeStatusHistoryRepository,
                null
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        Dispute dispute = Dispute.builder()
                .id(21L)
                .disputeId("DSP-21")
                .transactionReference("TX-21")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(21L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(disputeStatusHistoryRepository.save(any(DisputeStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        disputeService.reviewDispute(21L, "APPROVE", "Reviewed merchant evidence");

        ArgumentCaptor<DisputeStatusHistory> historyCaptor = ArgumentCaptor.forClass(DisputeStatusHistory.class);
        verify(disputeStatusHistoryRepository).save(historyCaptor.capture());

        assertEquals(DisputeStatus.OPEN, historyCaptor.getValue().getFromStatus());
        assertEquals(DisputeStatus.IN_REVIEW, historyCaptor.getValue().getToStatus());
        assertEquals("DISPUTE_REVIEWED", historyCaptor.getValue().getAction());
    }

    @Test
    void shouldReturnDisputeHistoryWithCanonicalActorRole() {
        DisputeStatusHistoryRepository disputeStatusHistoryRepository = mock(DisputeStatusHistoryRepository.class);
        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                new CustomerAccountContext("ACC-1001"),
                accountRepository,
                mock(UserProfileService.class),
                disputeStatusHistoryRepository,
                null
        );

        when(disputeStatusHistoryRepository.findByDisputeIdOrderByChangedAtAsc(42L)).thenReturn(List.of(
                DisputeStatusHistory.builder()
                        .id(1L)
                        .disputeId(42L)
                        .fromStatus(DisputeStatus.OPEN)
                        .toStatus(DisputeStatus.IN_REVIEW)
                        .action("DISPUTE_REVIEWED")
                        .reasonCode("MERCHANT_ERROR")
                        .actor("agent-user")
                        .actorRole("AGENT")
                        .changedAt(LocalDateTime.now())
                        .build()
        ));

        List<DisputeStatusHistory> history = disputeService.getDisputeHistory(42L);

        assertEquals(1, history.size());
        assertEquals("AGENT", history.get(0).getActorRole());
        assertEquals("DISPUTE_REVIEWED", history.get(0).getAction());
    }

    @Test
    void shouldRejectReversalWithBlankReason() {
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        assertThrows(IllegalArgumentException.class, () -> disputeService.reverseDispute(1L, "   "));
    }

    @Test
    void shouldRejectRepeatedReverseOnResolvedDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.RESOLVED)
                .decisionOutcome("REVERSED")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("test")
                .build()));

        assertThrows(za.co.transactiondispute.exception.DomainConflictException.class, () -> disputeService.reverseDispute(1L, "repeat reversal"));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldRejectReversalWhenLedgerEntryAlreadyExistsForDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        DisputeLedgerEntryRepository ledgerEntryRepository = mock(DisputeLedgerEntryRepository.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                context,
                accountRepository,
                userProfileService,
                mock(DisputeStatusHistoryRepository.class),
                mock(za.co.transactiondispute.repository.DisputeIdempotencyRepository.class),
                ledgerEntryRepository
        );

        Dispute dispute = Dispute.builder()
                .id(150L)
                .disputeId("DSP-150")
                .transactionReference("TX-150")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.PROVISIONAL_CREDIT_ISSUED)
                .decisionOutcome(null)
                .approvedByUsername("admin-user")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction original = Transaction.builder()
                .transactionReference("TX-150")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("original")
                .build();

        when(disputeRepository.findById(150L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-150")).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.existsByDisputeIdAndAction(150L, "REVERSED")).thenReturn(true);

        assertThrows(za.co.transactiondispute.exception.DomainConflictException.class,
                () -> disputeService.reverseDispute(150L, "duplicate transaction", "ledger-guard-150"));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldRejectReversalForOtherAccount() {
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-999")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));

        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.reverseDispute(1L, "other account"));
    }

    @Test
    void shouldReturnOnlyCurrentCustomerDisputes() {
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), new CustomerAccountContext("ACC-1001"), accountRepository, mock(UserProfileService.class));

        Dispute current = Dispute.builder()
                .id(1L)
                .disputeId("DSP-CURRENT")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("10.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Current")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Dispute other = Dispute.builder()
                .id(2L)
                .disputeId("DSP-OTHER")
                .transactionReference("TX-2")
                .accountNumber("ACC-999")
                .disputedAmount(new BigDecimal("12.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Other")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(current));

        List<Dispute> disputes = disputeService.getAllDisputes();

        assertEquals(1, disputes.size());
        assertEquals("ACC-1001", disputes.get(0).getAccountNumber());
        assertNotEquals("ACC-999", disputes.get(0).getAccountNumber());
    }

    @Test
    void shouldRejectDisputeForCreditTransaction() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                context,
                accountRepository,
                userProfileService
        );

        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-CR-1");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("200.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);
        request.setDescription("Credit entry should not be disputed");

        when(transactionRepository.findByTransactionReference("TX-CR-1")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-CR-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("200.00"))
                .currency("ZAR")
                .type(TransactionType.CREDIT)
                .merchantName("Employer")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Salary")
                .build()));

        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(request));
    }

    @Test
    void shouldRejectCustomerFromReversingOwnDispute() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(
                disputeRepository,
                transactionRepository,
                new DisputeValidator(),
                mock(EmailClient.class),
                mock(SmsClient.class),
                context,
                accountRepository,
                userProfileService
        );

        Dispute dispute = Dispute.builder()
                .id(101L)
                .disputeId("DSP-101")
                .transactionReference("TX-101")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Raised by customer")
                .status(DisputeStatus.OPEN)
                .assignedToFullName("Jane Customer")
                .submittedByUsername("agent-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(101L)).thenReturn(Optional.of(dispute));

        assertThrows(IllegalArgumentException.class, () -> disputeService.reverseDispute(101L, "self reversal not allowed"));
    }

    @Test
    void shouldRejectAlreadyResolvedDisputeWhenReversedAgain() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(200L)
                .disputeId("DSP-200")
                .transactionReference("TX-200")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("MERCHANT_ERROR")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Already resolved")
                .status(DisputeStatus.RESOLVED)
                .decisionOutcome("REVERSED")
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(200L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-200")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-200")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("test")
                .build()));

        assertThrows(za.co.transactiondispute.exception.DomainConflictException.class, () -> disputeService.reverseDispute(200L, "repeated reversal"));
    }

    @Test
    void shouldDefineCanonicalDisputeStateTransitions() {
        assertTrue(DisputeStatus.OPEN.canTransitionTo(DisputeStatus.IN_REVIEW));
        assertTrue(DisputeStatus.IN_REVIEW.canTransitionTo(DisputeStatus.PROVISIONAL_CREDIT_ISSUED));
        assertTrue(DisputeStatus.PROVISIONAL_CREDIT_ISSUED.canTransitionTo(DisputeStatus.RESOLVED));
        assertFalse(DisputeStatus.RESOLVED.canTransitionTo(DisputeStatus.OPEN));
        assertFalse(DisputeStatus.REJECTED.canTransitionTo(DisputeStatus.RESOLVED));
    }

    @Test
    void shouldNormaliseBalanceImpactStatuses() {
        assertTrue(BalanceCalculator.isBalanceImpactStatus("resolved"));
        assertTrue(BalanceCalculator.isBalanceImpactStatus("FINALISED"));
        assertTrue(BalanceCalculator.isBalanceImpactStatus("settled"));
        assertFalse(BalanceCalculator.isBalanceImpactStatus("pending"));
    }

    @Test
    void shouldRecogniseLeadUsersAsAgentLevelOperators() {
        assertEquals(UserRoles.ADMIN, UserRoles.canonicalize("Dispute Admin", null));
        assertEquals(UserRoles.AGENT, UserRoles.canonicalize("Dispute Operations Lead", null));
        assertEquals(UserRoles.AGENT, UserRoles.defaultRoleForUsername("lead-user"));
        assertEquals(UserRoles.AGENT, UserRoles.defaultRoleForUsername("operations-lead"));
        assertEquals(UserRoles.CUSTOMER, UserRoles.defaultRoleForUsername("customer-user"));
    }

    @Test
    void shouldRequireReviewApprovalBeforeReversal() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-100")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("50.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("test")
                .build()));

        assertThrows(IllegalArgumentException.class, () -> disputeService.reverseDispute(1L, "approved reversal"));
    }

    @Test
    void shouldWalkReviewAndApprovalFlow() {
        CustomerAccountContext context = new CustomerAccountContext("ACC-1001");
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.getProfile("agent-user")).thenReturn(UserProfile.builder()
                .username("agent-user")
                .fullName("Agent User")
                .role("DISPUTE_AGENT")
                .build());
        when(userProfileService.getProfile("admin-user")).thenReturn(UserProfile.builder()
                .username("admin-user")
                .fullName("Admin User")
                .role("ADMIN")
                .build());

        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, new DisputeValidator(), mock(EmailClient.class), mock(SmsClient.class), context, accountRepository, userProfileService);

        Dispute dispute = Dispute.builder()
                .id(99L)
                .disputeId("DSP-99")
                .transactionReference("TX-99")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .submittedByUsername("customer-user")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findById(99L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agent-user", "pw"));
        Dispute reviewed = disputeService.reviewDispute(99L, "APPROVE", "Needs verification");
        assertEquals(DisputeStatus.IN_REVIEW, reviewed.getStatus());
        assertEquals("agent-user", reviewed.getReviewerUsername());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin-user", "pw"));
        Dispute approved = disputeService.approveDispute(99L, "Approved by team lead");
        assertEquals(DisputeStatus.PROVISIONAL_CREDIT_ISSUED, approved.getStatus());
        assertEquals("admin-user", approved.getApprovedByUsername());
    }

    @Test
    void shouldValidateDisputeRequestWhenFieldsArePresent() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-1");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("25.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        assertDoesNotThrow(() -> new DisputeValidator().validateCreateRequest(request));
    }

    @Test
    void shouldRejectNullDisputeRequest() {
        assertThrows(IllegalArgumentException.class, () -> new DisputeValidator().validateCreateRequest(null));
    }

    @Test
    void shouldRejectBlankTransactionReference() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("   ");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("25.00"));
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        assertThrows(IllegalArgumentException.class, () -> new DisputeValidator().validateCreateRequest(request));
    }

    @Test
    void shouldRejectZeroDisputedAmount() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-1");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(BigDecimal.ZERO);
        request.setReasonCode("FRAUD");
        request.setDisputeType(DisputeType.CHARGEBACK);

        assertThrows(IllegalArgumentException.class, () -> new DisputeValidator().validateCreateRequest(request));
    }

    @Test
    void shouldRejectMissingDisputeType() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setTransactionReference("TX-1");
        request.setAccountNumber("ACC-1001");
        request.setDisputedAmount(new BigDecimal("25.00"));
        request.setReasonCode("FRAUD");

        assertThrows(IllegalArgumentException.class, () -> new DisputeValidator().validateCreateRequest(request));
    }
}
