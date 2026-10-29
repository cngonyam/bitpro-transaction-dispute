package za.co.transactiondispute.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeLedgerEntry;
import za.co.transactiondispute.entity.DisputeStatusHistory;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.DisputeType;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.DomainConflictException;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.entity.DisputeIdempotency;
import za.co.transactiondispute.repository.DisputeIdempotencyRepository;
import za.co.transactiondispute.repository.DisputeLedgerEntryRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.DisputeStatusHistoryRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.UserProfileService;
import za.co.transactiondispute.util.BalanceCalculator;
import za.co.transactiondispute.util.UserRoles;
import za.co.transactiondispute.validation.DisputeValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DisputeServiceImpl implements DisputeService {

    private static final String DISPUTE_ID_PREFIX = "DSP-";
    private static final String DEFAULT_ASSIGNEE = "Dispute Operations Team";
    private static final Logger log = LoggerFactory.getLogger(DisputeServiceImpl.class);
    private static final String REVERSED = "REVERSED";

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final DisputeValidator disputeValidator;
    private final EmailClient emailClient;
    private final SmsClient smsClient;
    private final CustomerAccountContext customerAccountContext;
    private final AccountRepository accountRepository;
    private final UserProfileService userProfileService;
    private final DisputeStatusHistoryRepository disputeStatusHistoryRepository;
    private final DisputeIdempotencyRepository disputeIdempotencyRepository;
    private final DisputeLedgerEntryRepository disputeLedgerEntryRepository;

    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              TransactionRepository transactionRepository,
                              DisputeValidator disputeValidator,
                              EmailClient emailClient,
                              SmsClient smsClient,
                              CustomerAccountContext customerAccountContext,
                              AccountRepository accountRepository,
                              UserProfileService userProfileService,
                              DisputeStatusHistoryRepository disputeStatusHistoryRepository,
                              DisputeIdempotencyRepository disputeIdempotencyRepository,
                              DisputeLedgerEntryRepository disputeLedgerEntryRepository) {
        this.disputeRepository = disputeRepository;
        this.transactionRepository = transactionRepository;
        this.disputeValidator = disputeValidator;
        this.emailClient = emailClient;
        this.smsClient = smsClient;
        this.customerAccountContext = customerAccountContext;
        this.accountRepository = accountRepository;
        this.userProfileService = userProfileService;
        this.disputeStatusHistoryRepository = disputeStatusHistoryRepository;
        this.disputeIdempotencyRepository = disputeIdempotencyRepository;
        this.disputeLedgerEntryRepository = disputeLedgerEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> getAllDisputes() {
        return disputeRepository.findByAccountNumber(customerAccountContext.getCurrentAccountNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dispute> getDisputeById(Long id) {
        return disputeRepository.findById(id)
                .filter(dispute -> dispute.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber()))
                .or(() -> {
                    throw new ResourceNotFoundException("Dispute not found with id: " + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeStatusHistory> getDisputeHistory(Long disputeId) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute id is required");
        }
        return disputeStatusHistoryRepository.findByDisputeIdOrderByChangedAtAsc(disputeId);
    }

    @Override
    @Transactional
    public Dispute createDispute(DisputeCreateRequest request, String idempotencyKey) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Authenticated user is required to create a dispute.");
        }

        String requestHash = hashRequestPayload("CREATE_DISPUTE", request);
        Optional<DisputeIdempotency> existing = findExistingIdempotency(username, "CREATE_DISPUTE", idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return disputeRepository.findById(existing.get().getDisputeId())
                    .orElseThrow(() -> new IllegalStateException("Stored idempotency record refers to a missing dispute."));
        }

        if (request != null && request.getDisputeType() == null) {
            request.setDisputeType(DisputeType.CHARGEBACK);
        }

        disputeValidator.validateCreateRequest(request);

        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        if (!request.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only submit disputes for their own account.");
        }

        Transaction transaction = resolveUniqueTransaction(request.getTransactionReference());

        if (!transaction.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only dispute transactions on their own account.");
        }
        if (TransactionType.CREDIT.equals(transaction.getType()) || TransactionType.REVERSAL.equals(transaction.getType())) {
            throw new IllegalArgumentException("Only debit transactions can be disputed.");
        }

        if (disputeRepository.findByTransactionReference(request.getTransactionReference()).isPresent()) {
            throw new IllegalArgumentException("A dispute already exists for transaction: " + request.getTransactionReference());
        }

        UserProfile currentProfile = userProfileService.getProfile(username);
        String normalizedRole = normalizeRole(currentProfile != null ? currentProfile.getRole() : null);

        BigDecimal disputedAmount = request.getDisputedAmount().abs();
        BigDecimal transactionAmount = transaction.getAmount().abs();
        if (disputedAmount.compareTo(transactionAmount) > 0) {
            throw new IllegalArgumentException("Disputed amount cannot exceed the original transaction amount.");
        }

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = Dispute.builder()
                .disputeId(DISPUTE_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionReference(transaction.getTransactionReference())
                .accountNumber(request.getAccountNumber())
                .disputedAmount(disputedAmount)
                .reasonCode(request.getReasonCode())
                .disputeType(request.getDisputeType())
                .description(request.getDescription())
                .assignedToFullName(isAgentRole(normalizedRole) && currentProfile != null ? currentProfile.getFullName() : DEFAULT_ASSIGNEE)
                .submittedByUsername(username)
                .provisionalCreditIssued(false)
                .merchantEvidenceProvided(Boolean.TRUE.equals(request.getMerchantEvidenceProvided()))
                .status(DisputeStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        appendStatusHistory(saved, null, saved.getStatus(), "DISPUTE_CREATED", request.getReasonCode(), username);
        persistIdempotency(username, "CREATE_DISPUTE", idempotencyKey, requestHash, saved);

        notifyAfterCommit(currentProfile, "Dispute Created", "Dispute " + saved.getDisputeId() + " has been created.",
                "Dispute " + saved.getDisputeId() + " is under review");

        return saved;
    }

    @Override
    @Transactional
    public Dispute reviewDispute(Long disputeId, String decision, String reviewNote) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute id is required");
        }

        String currentUser = currentUserKey();
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeId));

        if (dispute.getStatus() != DisputeStatus.OPEN && dispute.getStatus() != DisputeStatus.IN_REVIEW) {
            throw new IllegalArgumentException("Only open or in-review disputes can be reviewed.");
        }

        UserProfile currentProfile = userProfileService.getProfile(currentUser);
        if (currentProfile == null) {
            throw new IllegalArgumentException("Authenticated reviewer profile is required.");
        }
        ensureUserMayReviewDispute(currentProfile);

        String normalizedDecision = decision == null ? "" : decision.trim();
        if (normalizedDecision.isBlank()) {
            throw new IllegalArgumentException("A review decision is required.");
        }

        DisputeStatus previousStatus = dispute.getStatus();
        dispute.setReviewerUsername(currentUser);
        dispute.setReviewDecision(normalizedDecision);
        if (reviewNote != null && !reviewNote.trim().isBlank()) {
            dispute.setApprovalNote(reviewNote.trim());
        }
        dispute.setStatus(DisputeStatus.IN_REVIEW);
        dispute.setUpdatedAt(LocalDateTime.now());

        Dispute saved = disputeRepository.save(dispute);
        appendStatusHistory(saved, previousStatus, DisputeStatus.IN_REVIEW, "DISPUTE_REVIEWED", normalizedDecision, currentUser);
        return saved;
    }

    @Override
    @Transactional
    public Dispute approveDispute(Long disputeId, String approvalNote) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute id is required");
        }

        String currentUser = currentUserKey();
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeId));

        if (dispute.getStatus() != DisputeStatus.IN_REVIEW) {
            throw new IllegalArgumentException("Only in-review disputes can be approved.");
        }

        UserProfile currentProfile = userProfileService.getProfile(currentUser);
        if (currentProfile == null) {
            throw new IllegalArgumentException("Authenticated approver profile is required.");
        }
        ensureUserMayApproveDispute(dispute, currentProfile);

        String normalizedNote = approvalNote == null ? "" : approvalNote.trim();
        if (normalizedNote.isBlank()) {
            throw new IllegalArgumentException("An approval note is required.");
        }

        DisputeStatus previousStatus = dispute.getStatus();
        dispute.setApprovedByUsername(currentUser);
        dispute.setApprovalNote(normalizedNote);
        dispute.setProvisionalCreditIssued(Boolean.TRUE);
        dispute.setStatus(DisputeStatus.PROVISIONAL_CREDIT_ISSUED);
        dispute.setUpdatedAt(LocalDateTime.now());

        Dispute saved = disputeRepository.save(dispute);
        appendStatusHistory(saved, previousStatus, DisputeStatus.PROVISIONAL_CREDIT_ISSUED, "DISPUTE_APPROVED", normalizedNote, currentUser);
        return saved;
    }

    @Override
    @Transactional
    public Dispute reverseDispute(Long disputeId, String reasonCode, String idempotencyKey) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute id is required");
        }

        String normalizedReason = reasonCode == null ? "" : reasonCode.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A reversal reason is required");
        }

        String currentUser = currentUserKey();
        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();

        // Take a row lock before anything else so concurrent reversals of the same dispute are serialised.
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .filter(item -> item.getAccountNumber().equalsIgnoreCase(currentAccountNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeId));

        String requestHash = hashRequestPayload("REVERSE_DISPUTE", disputeId, reasonCode);
        Optional<DisputeIdempotency> existing = findExistingIdempotency(currentUser, "REVERSE_DISPUTE", idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return disputeRepository.findById(existing.get().getDisputeId())
                    .orElseThrow(() -> new IllegalStateException("Stored idempotency record refers to a missing dispute."));
        }

        ensureUserMayReverseDispute(dispute);

        Transaction originalTransaction = transactionRepository.findByTransactionReference(dispute.getTransactionReference())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + dispute.getTransactionReference()));

        if (!originalTransaction.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only reverse disputes for their own account.");
        }
        if (isReversalAlreadyApplied(dispute)) {
            throw new DomainConflictException("This dispute has already been resolved and cannot be reversed again.");
        }
        if (dispute.getStatus() == DisputeStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected disputes cannot be reversed.");
        }
        if (dispute.getStatus() != DisputeStatus.PROVISIONAL_CREDIT_ISSUED) {
            throw new IllegalArgumentException("Disputes must be approved before reversal can be posted.");
        }
        if (dispute.getApprovedByUsername() == null || dispute.getApprovedByUsername().isBlank()) {
            throw new IllegalArgumentException("Dispute must be approved by an authorised reviewer before reversal.");
        }
        ensureNoDuplicateReversalPosting(dispute);
        validateDisputeStatusTransition(dispute.getStatus(), DisputeStatus.RESOLVED, "REVERSE");

        BigDecimal reversalAmount = dispute.getDisputedAmount().abs();
        BigDecimal transactionAmount = originalTransaction.getAmount().abs();
        if (reversalAmount.compareTo(transactionAmount) > 0) {
            throw new IllegalArgumentException("Reversal amount cannot exceed the original transaction amount.");
        }

        DisputeStatus previousStatus = dispute.getStatus();
        dispute.setReversalReason(normalizedReason);
        dispute.setDecisionOutcome(REVERSED);
        dispute.setReversedByUsername(currentUser);
        dispute.setReversedByRole(resolveActorRole(currentUser));
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setUpdatedAt(LocalDateTime.now());

        Dispute saved = disputeRepository.save(dispute);
        createReversalLedgerEntry(saved, originalTransaction, normalizedReason);
        persistDisputeLedgerEntry(saved, originalTransaction, normalizedReason, currentUser);
        appendStatusHistory(saved, previousStatus, saved.getStatus(), "DISPUTE_REVERSED", normalizedReason, currentUserKey());
        persistIdempotency(currentUser, "REVERSE_DISPUTE", idempotencyKey, requestHash, saved);

        UserProfile customerProfile = saved.getSubmittedByUsername() == null ? null : userProfileService.getProfile(saved.getSubmittedByUsername());
        notifyAfterCommit(customerProfile, "Dispute Reversed", "Dispute " + saved.getDisputeId() + " was reversed for reason: " + normalizedReason,
                "Dispute " + saved.getDisputeId() + " has been resolved and reversed");

        return saved;
    }

    private boolean isReversalAlreadyApplied(Dispute dispute) {
        return dispute != null && (dispute.getStatus() == DisputeStatus.RESOLVED || REVERSED.equalsIgnoreCase(dispute.getDecisionOutcome()));
    }

    private void ensureNoDuplicateReversalPosting(Dispute dispute) {
        boolean reversalAlreadyRecorded = disputeLedgerEntryRepository.existsByDisputeIdAndAction(dispute.getId(), "REVERSED");
        if (reversalAlreadyRecorded) {
            throw new DomainConflictException("This dispute already has a reversal ledger entry and cannot be reversed again.");
        }
    }

    private void ensureUserMayReverseDispute(Dispute dispute) {
        if (dispute == null) {
            throw new IllegalArgumentException("Dispute is required.");
        }

        String currentUsername = currentUserKey();
        if (dispute.getSubmittedByUsername() != null && dispute.getSubmittedByUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("The customer who raised a dispute cannot reverse their own dispute.");
        }

        UserProfile currentProfile = userProfileService.getProfile(currentUsername);
        if (!canAuthorizeOperationalAction(currentProfile)) {
            throw new IllegalArgumentException("Only authorised dispute agents or administrators can reverse a dispute.");
        }
    }

    private void ensureUserMayReviewDispute(UserProfile currentProfile) {
        if (!canAuthorizeOperationalAction(currentProfile)) {
            throw new IllegalArgumentException("Only dispute agents or administrators can review disputes.");
        }
    }

    private void ensureUserMayApproveDispute(Dispute dispute, UserProfile currentProfile) {
        String role = currentProfile != null ? currentProfile.getRole() : null;
        if (!UserRoles.isAdminRole(role) && !UserRoles.isLeadRole(role)) {
            throw new IllegalArgumentException("Only authorised dispute administrators or lead-level operators can approve disputes.");
        }
        String currentUsername = currentUserKey();
        if (dispute.getSubmittedByUsername() != null && dispute.getSubmittedByUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("The person who raised a dispute cannot approve it.");
        }
    }

    private void validateDisputeStatusTransition(DisputeStatus current, DisputeStatus next, String action) {
        if (current == null) {
            return;
        }
        if (next == null) {
            throw new IllegalArgumentException("Dispute transition target is required when " + action + " is requested.");
        }
        if (!current.canTransitionTo(next)) {
            throw new IllegalArgumentException("Illegal dispute transition from " + current + " to " + next + ".");
        }
    }

    private Transaction resolveUniqueTransaction(String transactionReference) {
        return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + transactionReference));
    }

    private void appendStatusHistory(Dispute dispute, DisputeStatus fromStatus, DisputeStatus toStatus, String action, String reasonCode, String actor) {
        String actorRole = resolveActorRole(actor);
        DisputeStatusHistory entry = DisputeStatusHistory.builder()
                .disputeId(dispute.getId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .action(action)
                .reasonCode(reasonCode)
                .actor(actor)
                .actorRole(actorRole)
                .changedAt(LocalDateTime.now())
                .build();
        disputeStatusHistoryRepository.save(entry);
    }

    private String resolveActorRole(String username) {
        if (username == null || username.isBlank()) {
            return "CUSTOMER";
        }

        UserProfile profile = userProfileService != null ? userProfileService.getProfile(username) : null;
        String canonicalRole = UserRoles.canonicalize(profile != null ? profile.getRole() : null, username);
        return canonicalRole == null || canonicalRole.isBlank() ? UserRoles.defaultRoleForUsername(username) : canonicalRole;
    }

    private Transaction createReversalLedgerEntry(Dispute dispute, Transaction originalTransaction, String reasonCode) {
        if (dispute == null || dispute.getAccountNumber() == null || dispute.getDisputedAmount() == null) {
            return null;
        }

        BigDecimal reversalAmount = dispute.getDisputedAmount().abs();
        String currency = originalTransaction != null && originalTransaction.getCurrency() != null
                ? originalTransaction.getCurrency()
                : "ZAR";

        Transaction reversalEntry = Transaction.builder()
                .transactionReference(buildReversalTransactionReference())
                .accountNumber(dispute.getAccountNumber())
                .amount(reversalAmount)
                .currency(currency)
                .type(TransactionType.CREDIT)
                .merchantName("DISPUTE REVERSAL")
                .status("RESOLVED")
                .transactionDate(LocalDateTime.now())
                .description("Dispute reversal for " + dispute.getDisputeId() + " - " + reasonCode)
                .relatedDisputeId(dispute.getId())
                .build();

        Transaction savedReversal = transactionRepository.save(reversalEntry);
        reconcileAccountBalance(dispute.getAccountNumber());
        return savedReversal;
    }

    private void persistDisputeLedgerEntry(Dispute dispute, Transaction originalTransaction, String reasonCode, String actorUsername) {
        BigDecimal amount = dispute.getDisputedAmount() == null ? BigDecimal.ZERO : dispute.getDisputedAmount().abs();
        String currency = originalTransaction != null && originalTransaction.getCurrency() != null ? originalTransaction.getCurrency() : "ZAR";
        String actorRole = resolveActorRole(actorUsername);

        DisputeLedgerEntry ledgerEntry = DisputeLedgerEntry.builder()
                .disputeId(dispute.getId())
                .transactionReference(dispute.getTransactionReference())
                .accountNumber(dispute.getAccountNumber())
                .amount(amount)
                .currency(currency)
                .action("REVERSED")
                .actor(actorUsername)
                .actorRole(actorRole)
                .description("Dispute reversal for " + dispute.getDisputeId() + " - " + reasonCode)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        disputeLedgerEntryRepository.save(ledgerEntry);
    }

    private void reconcileAccountBalance(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return;
        }

        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return;
        }

        List<Transaction> accountTransactions = transactionRepository.findByAccountNumber(accountNumber);
        BigDecimal finalizedBalance = BalanceCalculator.calculateFinalizedBalance(accountTransactions);
        BigDecimal availableBalance = BalanceCalculator.calculateAvailableBalance(accountTransactions);

        account.setBalance(finalizedBalance);
        account.setAvailableBalance(availableBalance);
        accountRepository.save(account);
    }

    private String buildReversalTransactionReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TX-" + datePart + "-REV-" + randomPart;
    }

    private Optional<DisputeIdempotency> findExistingIdempotency(String username, String operation, String idempotencyKey, String requestHash) {
        Optional<DisputeIdempotency> existing = disputeIdempotencyRepository
                .findByUsernameAndOperationAndIdempotencyKey(username, operation, idempotencyKey);
        if (existing.isPresent() && !existing.get().getRequestHash().equals(requestHash)) {
            throw new IllegalArgumentException("Idempotency key already used for a different request payload.");
        }
        return existing;
    }

    private void persistIdempotency(String username, String operation, String idempotencyKey, String requestHash, Dispute dispute) {
        disputeIdempotencyRepository.save(DisputeIdempotency.builder()
                .username(username)
                .operation(operation)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .responseHash(hashRequestPayload(operation + "_RESPONSE", dispute))
                .disputeId(dispute.getId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private String hashRequestPayload(String operation, Object... parts) {
        StringBuilder builder = new StringBuilder(operation);
        for (Object part : parts) {
            if (part == null) {
                builder.append(":null");
            } else {
                builder.append(":").append(part.toString());
            }
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            return builder.toString();
        }
    }

    private String normalizeRole(String role) {
        return UserRoles.canonicalize(role, null);
    }

    private boolean isAgentRole(String normalizedRole) {
        return UserRoles.isAgentRole(normalizedRole);
    }

    private boolean isAdminRole(String normalizedRole) {
        return UserRoles.isAdminRole(normalizedRole);
    }

    private boolean canAuthorizeOperationalAction(UserProfile currentProfile) {
        String normalizedRole = normalizeRole(currentProfile != null ? currentProfile.getRole() : null);
        return isAgentRole(normalizedRole) || isAdminRole(normalizedRole);
    }

    private String currentUserKey() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "anonymous";
    }

    private void notifyAfterCommit(UserProfile recipient, String subject, String emailBody, String smsBody) {
        if (recipient == null) {
            return;
        }
        Runnable send = () -> {
            try {
                if (recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
                    emailClient.sendEmail(recipient.getEmail(), subject, emailBody);
                }
                if (recipient.getPhone() != null && !recipient.getPhone().isBlank()) {
                    smsClient.sendSms(recipient.getPhone(), smsBody);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to send dispute notification '{}': {}", subject, ex.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
