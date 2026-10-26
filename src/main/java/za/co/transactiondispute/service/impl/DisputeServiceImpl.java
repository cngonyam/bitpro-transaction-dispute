package za.co.transactiondispute.service.impl;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.UserProfileService;
import za.co.transactiondispute.validation.DisputeValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final DisputeValidator disputeValidator;
    private final EmailClient emailClient;
    private final SmsClient smsClient;
    private final CustomerAccountContext customerAccountContext;
    private final AccountRepository accountRepository;
    private final UserProfileService userProfileService;

    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              TransactionRepository transactionRepository,
                              DisputeValidator disputeValidator,
                              EmailClient emailClient,
                              SmsClient smsClient,
                              CustomerAccountContext customerAccountContext,
                              AccountRepository accountRepository,
                              UserProfileService userProfileService) {
        this.disputeRepository = disputeRepository;
        this.transactionRepository = transactionRepository;
        this.disputeValidator = disputeValidator;
        this.emailClient = emailClient;
        this.smsClient = smsClient;
        this.customerAccountContext = customerAccountContext;
        this.accountRepository = accountRepository;
        this.userProfileService = userProfileService;
    }

    @Override
    public List<Dispute> getAllDisputes() {
        return disputeRepository.findByAccountNumber(customerAccountContext.getCurrentAccountNumber());
    }

    @Override
    public Optional<Dispute> getDisputeById(Long id) {
        return disputeRepository.findById(id)
                .filter(dispute -> dispute.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber()))
                .or(() -> {
                    throw new ResourceNotFoundException("Dispute not found with id: " + id);
                });
    }

    @Override
    public Dispute createDispute(DisputeCreateRequest request) {
        if (request != null && request.getDisputeType() == null) {
            request.setDisputeType(DisputeType.CHARGEBACK);
        }

        disputeValidator.validateCreateRequest(request);

        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        if (!request.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only submit disputes for their own account.");
        }

        Transaction transaction = transactionRepository.findByTransactionReference(request.getTransactionReference())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + request.getTransactionReference()));

        if (!transaction.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only dispute transactions on their own account.");
        }

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "dev-user";
        UserProfile assignedProfile = userProfileService.getProfile(username);

        BigDecimal disputedAmount = request.getDisputedAmount() == null ? BigDecimal.ZERO : request.getDisputedAmount().abs();

        DisputeStatus disputeStatus = DisputeStatus.OPEN;
        String decisionOutcome = request.getDecisionOutcome();
        boolean provisionalCreditIssued = Boolean.TRUE.equals(request.getProvisionalCreditIssued());

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = Dispute.builder()
                .disputeId("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionReference(transaction.getTransactionReference())
                .accountNumber(request.getAccountNumber())
                .disputedAmount(disputedAmount)
                .reasonCode(request.getReasonCode())
                .disputeType(request.getDisputeType())
                .description(request.getDescription())
                .assignedToFullName(assignedProfile.getFullName())
                .provisionalCreditIssued(provisionalCreditIssued)
                .merchantEvidenceProvided(Boolean.TRUE.equals(request.getMerchantEvidenceProvided()))
                .decisionOutcome(decisionOutcome)
                .status(disputeStatus)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        emailClient.sendEmail("customer@example.com", "Dispute Created", "Dispute " + saved.getDisputeId() + " has been created.");
        smsClient.sendSms("+27123456789", "Dispute " + saved.getDisputeId() + " is under review");

        return saved;
    }

    @Override
    public Dispute reverseDispute(Long disputeId, String reasonCode) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute id is required");
        }

        String normalizedReason = reasonCode == null ? "" : reasonCode.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A reversal reason is required");
        }

        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        Dispute dispute = disputeRepository.findById(disputeId)
                .filter(item -> item.getAccountNumber().equalsIgnoreCase(currentAccountNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeId));

        Transaction originalTransaction = transactionRepository.findByTransactionReference(dispute.getTransactionReference())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + dispute.getTransactionReference()));

        if (!originalTransaction.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only reverse disputes for their own account.");
        }

        BigDecimal reversalAmount = dispute.getDisputedAmount() == null ? BigDecimal.ZERO : dispute.getDisputedAmount().abs();
        Transaction reversalTransaction = Transaction.builder()
                .transactionReference("REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .accountNumber(originalTransaction.getAccountNumber())
                .amount(reversalAmount)
                .currency(originalTransaction.getCurrency())
                .type(TransactionType.CREDIT)
                .merchantName("Dispute reversal")
                .status("FINALISED")
                .transactionDate(LocalDateTime.now())
                .description("Dispute reversal for " + originalTransaction.getTransactionReference() + " - " + normalizedReason)
                .build();
        transactionRepository.save(reversalTransaction);

        originalTransaction.setStatus("REVERSED");
        transactionRepository.save(originalTransaction);

        dispute.setReasonCode(normalizedReason);
        dispute.setDecisionOutcome("REVERSED");
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setUpdatedAt(LocalDateTime.now());
        String currentDescription = dispute.getDescription() == null ? "" : dispute.getDescription();
        dispute.setDescription((currentDescription + (currentDescription.isBlank() ? "" : " ") + "Reversed: " + normalizedReason).trim());

        Dispute saved = disputeRepository.save(dispute);
        emailClient.sendEmail("customer@example.com", "Dispute Reversed", "Dispute " + saved.getDisputeId() + " was reversed for reason: " + normalizedReason);
        smsClient.sendSms("+27123456789", "Dispute " + saved.getDisputeId() + " has been resolved and reversed");

        return saved;
    }
}
