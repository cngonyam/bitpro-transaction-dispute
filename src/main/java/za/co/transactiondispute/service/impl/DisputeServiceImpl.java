package za.co.transactiondispute.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeStatusHistory;
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
import za.co.transactiondispute.repository.DisputeStatusHistoryRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.UserProfileService;
import za.co.transactiondispute.util.BalanceCalculator;
import za.co.transactiondispute.validation.DisputeValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DisputeServiceImpl implements DisputeService {

    private static final String DISPUTE_ID_PREFIX = "DSP-";
    private static final String FINALISED = "FINALISED";
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
    private final Map<String, Dispute> createIdempotencyCache = new ConcurrentHashMap<>();
    private final Map<String, Dispute> reverseIdempotencyCache = new ConcurrentHashMap<>();

    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              TransactionRepository transactionRepository,
                              DisputeValidator disputeValidator,
                              EmailClient emailClient,
                              SmsClient smsClient,
                              CustomerAccountContext customerAccountContext,
                              AccountRepository accountRepository,
                              UserProfileService userProfileService) {
        this(disputeRepository, transactionRepository, disputeValidator, emailClient, smsClient, customerAccountContext, accountRepository, userProfileService, null);
    }

    @Autowired
    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              TransactionRepository transactionRepository,
                              DisputeValidator disputeValidator,
                              EmailClient emailClient,
                              SmsClient smsClient,
                              CustomerAccountContext customerAccountContext,
                              AccountRepository accountRepository,
                              UserProfileService userProfileService,
                              DisputeStatusHistoryRepository disputeStatusHistoryRepository) {
        this.disputeRepository = disputeRepository;
        this.transactionRepository = transactionRepository;
        this.disputeValidator = disputeValidator;
        this.emailClient = emailClient;
        this.smsClient = smsClient;
        this.customerAccountContext = customerAccountContext;
        this.accountRepository = accountRepository;
        this.userProfileService = userProfileService;
        this.disputeStatusHistoryRepository = disputeStatusHistoryRepository;
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
    @Transactional
    public Dispute createDispute(DisputeCreateRequest request) {
        return createDispute(request, "legacy-create-" + UUID.randomUUID());
    }

    @Override
    @Transactional
    public Dispute createDispute(DisputeCreateRequest request, String idempotencyKey) {
        String cacheKey = buildIdempotencyKey("create", idempotencyKey);
        Dispute cached = createIdempotencyCache.get(cacheKey);
        if (cached != null) {
            return cached;
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

        List<Dispute> existingDisputes = disputeRepository.findAllByTransactionReference(request.getTransactionReference());
        if (existingDisputes.isEmpty()) {
            Optional<Dispute> legacyMatch = disputeRepository.findByTransactionReference(request.getTransactionReference());
            if (legacyMatch.isPresent()) {
                existingDisputes = List.of(legacyMatch.get());
            }
        }
        if (!existingDisputes.isEmpty()) {
            boolean hasCurrentAccountDispute = existingDisputes.stream()
                    .anyMatch(existing -> existing.getAccountNumber().equalsIgnoreCase(currentAccountNumber));
            if (hasCurrentAccountDispute) {
                throw new IllegalArgumentException("A dispute already exists for transaction: " + request.getTransactionReference());
            }
            if (existingDisputes.size() > 1) {
                throw new IllegalStateException("Duplicate dispute references detected for transaction: " + request.getTransactionReference());
            }
        }

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Authenticated user is required to create a dispute.");
        }
        UserProfile assignedProfile = userProfileService.getProfile(username);

        BigDecimal disputedAmount = request.getDisputedAmount() == null ? BigDecimal.ZERO : request.getDisputedAmount().abs();
        BigDecimal transactionAmount = transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount().abs();
        if (disputedAmount.compareTo(transactionAmount) > 0) {
            throw new IllegalArgumentException("Disputed amount cannot exceed the original transaction amount.");
        }

        DisputeStatus disputeStatus = DisputeStatus.OPEN;
        String decisionOutcome = null;
        boolean provisionalCreditIssued = false;

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = Dispute.builder()
                .disputeId(DISPUTE_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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
        appendStatusHistory(saved, null, saved.getStatus(), "DISPUTE_CREATED", request.getReasonCode(), username);
        Dispute previous = createIdempotencyCache.putIfAbsent(cacheKey, saved);
        if (previous != null) {
            return previous;
        }

        emailClient.sendEmail("customer@example.com", "Dispute Created", "Dispute " + saved.getDisputeId() + " has been created.");
        smsClient.sendSms("+27832180551", "Dispute " + saved.getDisputeId() + " is under review");

        return saved;
    }

    @Override
    @Transactional
    public Dispute reverseDispute(Long disputeId, String reasonCode) {
        return reverseDispute(disputeId, reasonCode, "legacy-reverse-" + UUID.randomUUID());
    }

    @Override
    @Transactional
    public Dispute reverseDispute(Long disputeId, String reasonCode, String idempotencyKey) {
        String cacheKey = buildIdempotencyKey("reverse", idempotencyKey, String.valueOf(disputeId));
        Dispute cached = reverseIdempotencyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

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
        if (isReversalAlreadyApplied(dispute)) {
            return dispute;
        }
        if (dispute.getStatus() == DisputeStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected disputes cannot be reversed.");
        }
        validateDisputeStatusTransition(dispute.getStatus(), DisputeStatus.RESOLVED, "REVERSE");

        BigDecimal reversalAmount = dispute.getDisputedAmount() == null ? BigDecimal.ZERO : dispute.getDisputedAmount().abs();
        BigDecimal transactionAmount = originalTransaction.getAmount() == null ? BigDecimal.ZERO : originalTransaction.getAmount().abs();
        if (reversalAmount.compareTo(transactionAmount) > 0) {
            throw new IllegalArgumentException("Reversal amount cannot exceed the original transaction amount.");
        }

        DisputeStatus previousStatus = dispute.getStatus();
        dispute.setReasonCode(normalizedReason);
        dispute.setDecisionOutcome(REVERSED);
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setUpdatedAt(LocalDateTime.now());
        String currentDescription = dispute.getDescription() == null ? "" : dispute.getDescription();
        dispute.setDescription((currentDescription + (currentDescription.isBlank() ? "" : " ") + "Reversed: " + normalizedReason).trim());

        Dispute saved = disputeRepository.save(dispute);
        createReversalLedgerEntry(saved, originalTransaction, normalizedReason);
        appendStatusHistory(saved, previousStatus, saved.getStatus(), "DISPUTE_REVERSED", normalizedReason, currentUserKey());
        Dispute previous = reverseIdempotencyCache.putIfAbsent(cacheKey, saved);
        if (previous != null) {
            return previous;
        }

        emailClient.sendEmail("customer@example.com", "Dispute Reversed", "Dispute " + saved.getDisputeId() + " was reversed for reason: " + normalizedReason);
        smsClient.sendSms("+27123456789", "Dispute " + saved.getDisputeId() + " has been resolved and reversed");

        return saved;
    }

    private boolean isReversalAlreadyApplied(Dispute dispute) {
        return dispute != null && (dispute.getStatus() == DisputeStatus.RESOLVED || REVERSED.equalsIgnoreCase(dispute.getDecisionOutcome()));
    }

    private void validateDisputeStatusTransition(DisputeStatus current, DisputeStatus next, String action) {
        if (current == null) {
            return;
        }
        if (current == DisputeStatus.RESOLVED || current == DisputeStatus.REJECTED) {
            throw new IllegalArgumentException("Dispute cannot transition from " + current + " when " + action + " is requested.");
        }
        if (next == DisputeStatus.RESOLVED && current == DisputeStatus.OPEN) {
            return;
        }
        if (next == DisputeStatus.RESOLVED && current == DisputeStatus.IN_REVIEW) {
            return;
        }
        if (next == DisputeStatus.RESOLVED && current == DisputeStatus.PROVISIONAL_CREDIT_ISSUED) {
            return;
        }
        if (next == DisputeStatus.RESOLVED && current == DisputeStatus.MERCHANT_RESPONSE_PENDING) {
            return;
        }
        if (next == DisputeStatus.REJECTED && (current == DisputeStatus.OPEN || current == DisputeStatus.IN_REVIEW || current == DisputeStatus.MERCHANT_RESPONSE_PENDING)) {
            return;
        }
        if (current == DisputeStatus.OPEN && next == DisputeStatus.IN_REVIEW) {
            return;
        }
        if (current == DisputeStatus.IN_REVIEW && next == DisputeStatus.PROVISIONAL_CREDIT_ISSUED) {
            return;
        }
        if (current == DisputeStatus.MERCHANT_RESPONSE_PENDING && next == DisputeStatus.RESOLVED) {
            return;
        }
        throw new IllegalArgumentException("Illegal dispute transition from " + current + " to " + next + ".");
    }

    private Transaction resolveUniqueTransaction(String transactionReference) {
        List<Transaction> matches = transactionRepository.findAllByTransactionReference(transactionReference);
        if (matches.isEmpty()) {
            Optional<Transaction> legacyMatch = transactionRepository.findByTransactionReference(transactionReference);
            if (legacyMatch.isEmpty()) {
                throw new ResourceNotFoundException("Transaction not found with reference: " + transactionReference);
            }
            matches = List.of(legacyMatch.get());
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Duplicate transaction references detected for: " + transactionReference);
        }
        return matches.getFirst();
    }

    private void appendStatusHistory(Dispute dispute, DisputeStatus fromStatus, DisputeStatus toStatus, String action, String reasonCode, String actor) {
        if (disputeStatusHistoryRepository == null || dispute == null) {
            return;
        }

        DisputeStatusHistory entry = DisputeStatusHistory.builder()
                .disputeId(dispute.getId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .action(action)
                .reasonCode(reasonCode)
                .actor(actor)
                .changedAt(LocalDateTime.now())
                .build();
        disputeStatusHistoryRepository.save(entry);
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
                .build();

        Transaction savedReversal = transactionRepository.save(reversalEntry);
        reconcileAccountBalance(dispute.getAccountNumber());
        return savedReversal;
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

    private String buildIdempotencyKey(String operation, String... parts) {
        String user = currentUserKey();
        StringBuilder builder = new StringBuilder(operation).append(":").append(user);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                builder.append(":").append(part.trim());
            }
        }
        return builder.toString();
    }

    private String currentUserKey() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "anonymous";
    }
}
