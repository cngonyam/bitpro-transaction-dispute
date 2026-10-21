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
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.DisputeStatus;
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
    void shouldCoverValidatorsServicesAndControllers() {
        AccountService accountService = new AccountServiceImpl(accountRepository);
        Account account = Account.builder()
                .accountNumber("ACC-1001")
                .customerName("A")
                .currency("ZAR")
                .availableBalance(new BigDecimal("500.00"))
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
        TransactionService transactionService = new TransactionServiceImpl(transactionRepository, customerAccountContext);
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

        when(transactionRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(tx));
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        assertEquals(1, transactionService.getAllTransactions().size());
        assertEquals("TX-1", transactionService.getTransactionByReference("TX-1").orElseThrow().getTransactionReference());
        assertEquals(tx, transactionService.createTransaction(tx));
        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(null));
        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(Transaction.builder().accountNumber("ACC-999").build()));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> transactionService.getTransactionByReference("missing"));

        DisputeValidator disputeValidator = new DisputeValidator();
        DisputeCreateRequest validRequest = new DisputeCreateRequest();
        validRequest.setTransactionReference("TX-1");
        validRequest.setAccountNumber("ACC-1001");
        validRequest.setDisputedAmount(new BigDecimal("25.00"));
        validRequest.setReasonCode("FRAUD");
        validRequest.setDescription("Issue");
        assertDoesNotThrow(() -> disputeValidator.validateCreateRequest(validRequest));
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(null));
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(new DisputeCreateRequest()));
        DisputeCreateRequest missingReference = new DisputeCreateRequest();
        missingReference.setAccountNumber("ACC-1001");
        missingReference.setDisputedAmount(new BigDecimal("25.00"));
        missingReference.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(missingReference));

        DisputeCreateRequest missingAccount = new DisputeCreateRequest();
        missingAccount.setTransactionReference("TX-1");
        missingAccount.setDisputedAmount(new BigDecimal("25.00"));
        missingAccount.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(missingAccount));

        DisputeCreateRequest zeroAmount = new DisputeCreateRequest();
        zeroAmount.setTransactionReference("TX-1");
        zeroAmount.setAccountNumber("ACC-1001");
        zeroAmount.setDisputedAmount(BigDecimal.ZERO);
        zeroAmount.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(zeroAmount));

        DisputeCreateRequest blankReason = new DisputeCreateRequest();
        blankReason.setTransactionReference("TX-1");
        blankReason.setAccountNumber("ACC-1001");
        blankReason.setDisputedAmount(new BigDecimal("25.00"));
        blankReason.setReasonCode("   ");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankReason));

        DisputeCreateRequest blankReference = new DisputeCreateRequest();
        blankReference.setTransactionReference("   ");
        blankReference.setAccountNumber("ACC-1001");
        blankReference.setDisputedAmount(new BigDecimal("25.00"));
        blankReference.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankReference));

        DisputeCreateRequest blankAccount = new DisputeCreateRequest();
        blankAccount.setTransactionReference("TX-1");
        blankAccount.setAccountNumber("   ");
        blankAccount.setDisputedAmount(new BigDecimal("25.00"));
        blankAccount.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(blankAccount));

        DisputeCreateRequest nullAmount = new DisputeCreateRequest();
        nullAmount.setTransactionReference("TX-1");
        nullAmount.setAccountNumber("ACC-1001");
        nullAmount.setReasonCode("FRAUD");
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(nullAmount));

        DisputeCreateRequest nullReason = new DisputeCreateRequest();
        nullReason.setTransactionReference("TX-1");
        nullReason.setAccountNumber("ACC-1001");
        nullReason.setDisputedAmount(new BigDecimal("25.00"));
        assertThrows(IllegalArgumentException.class, () -> disputeValidator.validateCreateRequest(nullReason));

        EmailClient emailClient = mock(EmailClient.class);
        SmsClient smsClient = mock(SmsClient.class);
        DisputeService disputeService = new DisputeServiceImpl(disputeRepository, transactionRepository, disputeValidator, emailClient, smsClient, customerAccountContext);
        Dispute dispute = Dispute.builder()
                .id(1L)
                .disputeId("DSP-ABC123")
                .transactionReference("TX-1")
                .accountNumber("ACC-1001")
                .disputedAmount(new BigDecimal("25.00"))
                .reasonCode("FRAUD")
                .description("Issue")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(disputeRepository.findByAccountNumber("ACC-1001")).thenReturn(List.of(dispute));
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);

        assertEquals(1, disputeService.getAllDisputes().size());
        assertEquals("DSP-ABC123", disputeService.getDisputeById(1L).orElseThrow().getDisputeId());
        assertEquals(dispute, disputeService.createDispute(validRequest));
        assertThrows(IllegalArgumentException.class, () -> disputeService.createDispute(new DisputeCreateRequest()));
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.getDisputeById(99L));

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
        assertThrows(za.co.transactiondispute.exception.ResourceNotFoundException.class, () -> disputeService.createDispute(missingTransactionRequest));

        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(tx));
        TransactionController transactionController = new TransactionController(transactionService, new TransactionMapper());
        assertEquals(1, transactionController.getTransactions().size());
        assertEquals("TX-1", transactionController.getTransaction("TX-1").getBody().getTransactionReference());
        ResponseEntity<za.co.transactiondispute.dto.TransactionDto> newTransaction = transactionController.createTransaction(
                za.co.transactiondispute.dto.TransactionDto.builder().accountNumber("ACC-1001").transactionReference("TX-2").amount(new BigDecimal("50")).currency("ZAR").type(TransactionType.CREDIT).merchantName("Store").status("POSTED").transactionDate(LocalDateTime.now()).description("x").build());
        assertNotNull(newTransaction.getBody());

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
        assertEquals("ACC-1001", createdAccount.getBody().getAccountNumber());

        DisputeController disputeController = new DisputeController(disputeService);
        assertEquals(1, disputeController.getDisputes().size());
        assertEquals("DSP-ABC123", disputeController.getDispute(1L).getBody().getDisputeId());
        ResponseEntity<za.co.transactiondispute.dto.DisputeResponse> createdDispute = disputeController.createDispute(validRequest);
        assertEquals("DSP-ABC123", createdDispute.getBody().getDisputeId());
    }
}
