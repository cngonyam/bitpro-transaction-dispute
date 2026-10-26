package za.co.transactiondispute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.controller.AccountController;
import za.co.transactiondispute.controller.DisputeController;
import za.co.transactiondispute.controller.TransactionController;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.TransactionService;
import za.co.transactiondispute.service.impl.AccountServiceImpl;
import za.co.transactiondispute.service.impl.DisputeServiceImpl;
import za.co.transactiondispute.service.impl.TransactionServiceImpl;
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
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository);
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
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(resolvedCredit, pendingDebitOne, pendingDebitTwo));

        List<Account> accounts = accountService.getAllAccounts();

        assertEquals(1, accounts.size());
        assertEquals(new BigDecimal("12000.00"), accounts.get(0).getBalance());
        assertEquals(new BigDecimal("9500.00"), accounts.get(0).getAvailableBalance());
    }

    @Test
    void shouldCoverValidatorsServicesAndControllers() {
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository);
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("A")
                .currency("ZAR")
                .availableBalance(new BigDecimal("500.00"))
                .balance(new BigDecimal("500.00"))
                .status("ACTIVE")
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        assertEquals(1, accountService.getAllAccounts().size());
        assertEquals("ACC-1001", accountService.getAccountById(1L).orElseThrow().getAccountNumber());
        assertEquals(account, accountService.createAccount(account));
        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(null));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> accountService.getAccountById(99L));

        CustomerAccountContext customerAccountContext = new CustomerAccountContext("ACC-1001");
        TransactionService transactionService = new TransactionServiceImpl(transactionRepository, customerAccountContext, accountRepository);
        Transaction tx = Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("100.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("description")
                .build();

        TransactionDto debitDto = za.co.transactiondispute.dto.TransactionDto.builder()
                .transactionReference("TX-NEG")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("75.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Groceries")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("Debit amount")
                .build();
        assertEquals(new BigDecimal("-75.00"), new TransactionMapper().toEntity(debitDto).getAmount());

        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(tx));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);
        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, transactionService.getAllTransactions().size());
        assertEquals("TX-1", transactionService.getTransactionByReference("TX-1").orElseThrow().getTransactionReference());

        Transaction autoReferenceTransaction = Transaction.builder()
                .transactionReference(null)
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("25.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Auto Ref")
                .status("FINALISED")
                .transactionDate(LocalDateTime.now())
                .description("Generated reference")
                .build();
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(
                tx,
                autoReferenceTransaction
        ));

        Transaction savedAutoReferenceTransaction = transactionService.createTransaction(autoReferenceTransaction);
        assertNotNull(savedAutoReferenceTransaction.getTransactionReference());
        assertTrue(savedAutoReferenceTransaction.getTransactionReference().startsWith("TX-"));

        tx.setTransactionReference("TX-1");
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(tx, autoReferenceTransaction));
        assertEquals(tx, transactionService.createTransaction(tx));
        assertTrue(account.getAvailableBalance().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(account.getBalance().compareTo(BigDecimal.ZERO) <= 0);
        assertEquals(new BigDecimal("100.00"), account.getBalance().subtract(account.getAvailableBalance()));

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(Transaction.builder()
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("200.00"))
                .currency("ZAR")
                .type(TransactionType.CREDIT)
                .merchantName("Customer top-up")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("customer credit")
                .build()));

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(null));
        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(Transaction.builder().accountNumber("ACC-999").build()));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> transactionService.getTransactionByReference("missing"));

        DisputeValidator disputeValidator = new DisputeValidator();
        DisputeCreateRequest validRequest = new DisputeCreateRequest();
        validRequest.setTransactionReference("TX-1");
        validRequest.setAccountNumber("ACC-1001");
        validRequest.setDisputedAmount(new BigDecimal("25.00"));
        validRequest.setReasonCode("FRAUD");
        validRequest.setDisputeType(DisputeType.CHARGEBACK);
        validRequest.setDescription("Issue");
        assertDoesNotThrow(() -> disputeValidator.validateCreateRequest(validRequest));
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(null));
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(new DisputeCreateRequest()));
        DisputeCreateRequest missingReference = new DisputeCreateRequest();
        missingReference.setAccountNumber("ACC-1001");
        missingReference.setDisputedAmount(new BigDecimal("25.00"));
        missingReference.setReasonCode("FRAUD");
        missingReference.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(missingReference));

        DisputeCreateRequest missingAccount = new DisputeCreateRequest();
        missingAccount.setTransactionReference("TX-1");
        missingAccount.setDisputedAmount(new BigDecimal("25.00"));
        missingAccount.setReasonCode("FRAUD");
        missingAccount.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(missingAccount));

        DisputeCreateRequest zeroAmount = new DisputeCreateRequest();
        zeroAmount.setTransactionReference("TX-1");
        zeroAmount.setAccountNumber("ACC-1001");
        zeroAmount.setDisputedAmount(BigDecimal.ZERO);
        zeroAmount.setReasonCode("FRAUD");
        zeroAmount.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(zeroAmount));

        DisputeCreateRequest blankReason = new DisputeCreateRequest();
        blankReason.setTransactionReference("TX-1");
        blankReason.setAccountNumber("ACC-1001");
        blankReason.setDisputedAmount(new BigDecimal("25.00"));
        blankReason.setReasonCode("   ");
        blankReason.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankReason));

        DisputeCreateRequest blankReference = new DisputeCreateRequest();
        blankReference.setTransactionReference("   ");
        blankReference.setAccountNumber("ACC-1001");
        blankReference.setDisputedAmount(new BigDecimal("25.00"));
        blankReference.setReasonCode("FRAUD");
        blankReference.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankReference));

        DisputeCreateRequest blankAccount = new DisputeCreateRequest();
        blankAccount.setTransactionReference("TX-1");
        blankAccount.setAccountNumber("   ");
        blankAccount.setDisputedAmount(new BigDecimal("25.00"));
        blankAccount.setReasonCode("FRAUD");
        blankAccount.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankAccount));

        DisputeCreateRequest nullAmount = new DisputeCreateRequest();
        nullAmount.setTransactionReference("TX-1");
        nullAmount.setAccountNumber("ACC-1001");
        nullAmount.setReasonCode("FRAUD");
        nullAmount.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(nullAmount));

        DisputeCreateRequest nullReason = new DisputeCreateRequest();
        nullReason.setTransactionReference("TX-1");
        nullReason.setAccountNumber("ACC-1001");
        nullReason.setDisputedAmount(new BigDecimal("25.00"));
        nullReason.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(nullReason));

        DisputeCreateRequest missingType = new DisputeCreateRequest();
        missingType.setTransactionReference("TX-1");
        missingType.setAccountNumber("ACC-1001");
        missingType.setDisputedAmount(new BigDecimal("25.00"));
        missingType.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(missingType));

        EmailClient emailClient = mock(EmailClient.class);
        SmsClient smsClient = mock(SmsClient.class);
        za.co.transactiondispute.service.UserProfileService userProfileService = mock(za.co.transactiondispute.service.UserProfileService.class);
        when(userProfileService.getProfile(anyString())).thenReturn(UserProfile.builder()
                .username("dev-user")
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build());
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, disputeValidator, emailClient, smsClient, customerAccountContext, accountRepository, userProfileService);
        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-ABC123")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("Issue")
                .assignedToFullName("Ndumiso Ngonyama")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(dispute));
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, disputeService.getAllDisputes().size());
        assertEquals("DSP-ABC123", disputeService.getDisputeById(1L).orElseThrow().getDisputeId());
        assertEquals(DisputeType.CHARGEBACK, disputeService.createDispute(validRequest).getDisputeType());

        Account debitOrderAccount = Account.builder()
                .id(10L)
                .accountNumber("ACC-1001")
                .customerName("A")
                .currency("ZAR")
                .availableBalance(new BigDecimal("500.00"))
                .status("ACTIVE")
                .build();
        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(debitOrderAccount));
        DisputeCreateRequest normalChargebackRequest = new DisputeCreateRequest();
        normalChargebackRequest.setTransactionReference("TX-1");
        normalChargebackRequest.setAccountNumber("ACC-1001");
        normalChargebackRequest.setDisputedAmount(new BigDecimal("75.00"));
        normalChargebackRequest.setReasonCode("UNAUTHORISED_DEBIT");
        normalChargebackRequest.setDisputeType(DisputeType.CHARGEBACK);
        normalChargebackRequest.setDescription("Chargeback requested");

        Dispute chargebackDispute = disputeService.createDispute(normalChargebackRequest);
        assertEquals(DisputeStatus.OPEN, chargebackDispute.getStatus());
        assertEquals(DisputeType.CHARGEBACK, chargebackDispute.getDisputeType());

        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(new DisputeCreateRequest()));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.getDisputeById(99L));

        DisputeCreateRequest overLimitRequest = new DisputeCreateRequest();
        overLimitRequest.setTransactionReference("TX-1");
        overLimitRequest.setAccountNumber("ACC-1001");
        overLimitRequest.setDisputedAmount(new BigDecimal("250.00"));
        overLimitRequest.setReasonCode("OVER_LIMIT");
        overLimitRequest.setDisputeType(DisputeType.CHARGEBACK);
        overLimitRequest.setDescription("too high");
        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(overLimitRequest));

        Dispute resolvedDispute = Dispute.builder()
                .id(99L)
                .disputeId("DSP-RESOLVED")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("10.00"))
                .reasonCode("FRAUD")
                .disputeType(DisputeType.CHARGEBACK)
                .description("resolved")
                .status(DisputeStatus.RESOLVED)
                .decisionOutcome("REVERSED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findById(99L)).thenReturn(Optional.of(resolvedDispute));
        assertThrows(IllegalArgumentException.class, () -> disputeService.reverseDispute(99L, "repeat reversal"));

        Dispute otherAccountDispute = Dispute.builder()
                .id(2L)
                .disputeId("DSP-OTHER")
                .transactionReference("TX-2")
                .accountNumber("ACC-999")
                .disputedAmount(new BigDecimal("10.00"))
                .reasonCode("FRAUD")
                .description("Other")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findById(2L)).thenReturn(Optional.of(otherAccountDispute));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.getDisputeById(2L));

        DisputeCreateRequest otherAccountRequest = new DisputeCreateRequest();
        otherAccountRequest.setTransactionReference("TX-1");
        otherAccountRequest.setAccountNumber("ACC-999");
        otherAccountRequest.setDisputedAmount(new BigDecimal("25.00"));
        otherAccountRequest.setReasonCode("FRAUD");
        otherAccountRequest.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(otherAccountRequest));

        Transaction otherCustomerTransaction = Transaction.builder()
                .transactionReference("TX-1")
                .accountNumber("ACC-999")
                .amount(new BigDecimal("100.00"))
                .currency("ZAR")
                .type(TransactionType.DEBIT)
                .merchantName("Shop")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .description("outside account")
                .build();
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(otherCustomerTransaction));
        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(validRequest));

        when(transactionRepository.findByTransactionReference("missing-tx")).thenReturn(Optional.empty());
        DisputeCreateRequest missingTransactionRequest = new DisputeCreateRequest();
        missingTransactionRequest.setTransactionReference("missing-tx");
        missingTransactionRequest.setAccountNumber("ACC-1001");
        missingTransactionRequest.setDisputedAmount(new BigDecimal("25.00"));
        missingTransactionRequest.setReasonCode("FRAUD");
        missingTransactionRequest.setDisputeType(DisputeType.CHARGEBACK);
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.createDispute(missingTransactionRequest));

        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(tx));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(tx, autoReferenceTransaction));
        tx.setTransactionReference("TX-1");
        TransactionController transactionController = new TransactionController(transactionService, new TransactionMapper(), accountRepository);
        assertEquals(2, transactionController.getTransactions().size());
        assertEquals("TX-1", transactionController.getTransaction("TX-1").getBody().getTransactionReference());
        assertEquals(new BigDecimal("-125.00"), transactionController.getTransaction("TX-1").getBody().getAccountBalance());
        ResponseEntity<za.co.transactiondispute.dto.TransactionDto> newTransaction = transactionController.createTransaction(
                za.co.transactiondispute.dto.TransactionDto.builder().accountNumber("ACC-1001").transactionReference("TX-2").amount(new BigDecimal("50")).currency("ZAR").type(TransactionType.DEBIT).merchantName("Store").status("POSTED").transactionDate(LocalDateTime.now()).description("x").build());
        assertNotNull(newTransaction.getBody());
        assertTrue(newTransaction.getBody().getTransactionReference().startsWith("TX-"));

        AccountController accountController = new AccountController(accountService);
        assertEquals(1, accountController.getAccounts().size());
        assertEquals("ACC-1001", accountController.getAccount(1L).getBody().getAccountNumber());
        AccountDto accountDto = AccountDto.builder()
                .accountNumber("ACC-2000")
                .customerName("B")
                .currency("ZAR")
                .availableBalance(new BigDecimal("10.00"))
                .status("ACTIVE")
                .build();
        ResponseEntity<Account> createdAccount = accountController.createAccount(accountDto);
        assertEquals("ACC-2000", createdAccount.getBody().getAccountNumber());

        DisputeController disputeController = new DisputeController(disputeService);
        assertEquals(1, disputeController.getDisputes().size());
        assertEquals("DSP-ABC123", disputeController.getDispute(1L).getBody().getDisputeId());
        ResponseEntity<za.co.transactiondispute.dto.DisputeResponse> createdDispute = disputeController.createDispute(validRequest);
        assertNotNull(createdDispute.getBody().getDisputeId());
    }

    @Test
    void shouldNormalizeDebitAmountsWhenReconcilingBalance() {
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository);
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("Jane Smith")
                .currency("ZAR")
                .availableBalance(BigDecimal.ZERO)
                .balance(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("12000.00"))
                        .currency("ZAR")
                        .type(TransactionType.CREDIT)
                        .merchantName("Employer Payroll")
                        .status("FINALISED")
                        .transactionDate(LocalDateTime.now())
                        .description("Salary")
                        .build(),
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("1300.00"))
                        .currency("ZAR")
                        .type(TransactionType.DEBIT)
                        .merchantName("Card payment")
                        .status("FINALISED")
                        .transactionDate(LocalDateTime.now())
                        .description("Card")
                        .build(),
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("1200.00"))
                        .currency("ZAR")
                        .type(TransactionType.DEBIT)
                        .merchantName("Debit order")
                        .status("FINALISED")
                        .transactionDate(LocalDateTime.now())
                        .description("Order")
                        .build()
        ));

        Account reconciled = accountService.getAllAccounts().get(0);
        assertEquals(new BigDecimal("9500.00"), reconciled.getBalance());
        assertEquals(new BigDecimal("9500.00"), reconciled.getAvailableBalance());
    }

    @Test
    void shouldExcludePendingAmountsFromBalance() {
        AccountService accountService = new AccountServiceImpl(accountRepository, transactionRepository);
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("Jane Smith")
                .currency("ZAR")
                .availableBalance(BigDecimal.ZERO)
                .balance(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("12000.00"))
                        .currency("ZAR")
                        .type(TransactionType.CREDIT)
                        .merchantName("Employer Payroll")
                        .status("FINALISED")
                        .transactionDate(LocalDateTime.now())
                        .description("Salary")
                        .build(),
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("1300.00"))
                        .currency("ZAR")
                        .type(TransactionType.DEBIT)
                        .merchantName("Card payment")
                        .status("RESOLVED")
                        .transactionDate(LocalDateTime.now())
                        .description("Card")
                        .build(),
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("1200.00"))
                        .currency("ZAR")
                        .type(TransactionType.DEBIT)
                        .merchantName("Debit order")
                        .status("SETTLED")
                        .transactionDate(LocalDateTime.now())
                        .description("Order")
                        .build(),
                Transaction.builder()
                        .accountNumber("ACC-1001")
                        .amount(new BigDecimal("500.00"))
                        .currency("ZAR")
                        .type(TransactionType.CREDIT)
                        .merchantName("Pending refund")
                        .status("PENDING")
                        .transactionDate(LocalDateTime.now())
                        .description("Pending")
                        .build()
        ));

        Account reconciled = accountService.getAllAccounts().get(0);
        assertEquals(new BigDecimal("9500.00"), reconciled.getBalance());
        assertEquals(new BigDecimal("10000.00"), reconciled.getAvailableBalance());
    }
}
