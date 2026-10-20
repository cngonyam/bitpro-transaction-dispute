package za.co.transactiondispute.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.DisputeStatusUpdateRequest;
import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeEvent;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.enums.TransactionStatus;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.exception.ConflictException;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.mapper.DisputeMapper;
import za.co.transactiondispute.repository.AccountRepository;
import za.co.transactiondispute.repository.DisputeEventRepository;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.service.TransactionService;
import za.co.transactiondispute.validation.DisputeValidator;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;
    // A transaction with a dispute in one of these states cannot be disputed again.
    private static final Set<DisputeStatus> BLOCKING = EnumSet.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW, DisputeStatus.RESOLVED);
    private static final Set<DisputeStatus> AGENT_TARGETS = EnumSet.of(DisputeStatus.IN_REVIEW, DisputeStatus.RESOLVED, DisputeStatus.REJECTED);

    private final DisputeRepository disputeRepository;
    private final DisputeEventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final DisputeValidator disputeValidator;
    private final DisputeMapper disputeMapper;
    private final EmailClient emailClient;
    private final SmsClient smsClient;

    @Override
    @Transactional
    public DisputeResponse createDispute(DisputeCreateRequest request, Actor actor) {
        Account account = accountService.getAuthorisedAccount(request.getAccountNumber(), actor);
        Transaction transaction = transactionService.getTransactionForAccount(
                request.getTransactionReference(), account.getAccountNumber());

        disputeValidator.validate(request, account, transaction);
        if (disputeRepository.existsByTransactionReferenceAndStatusIn(transaction.getTransactionReference(), BLOCKING)) {
            throw new ConflictException("A dispute already exists for transaction " + transaction.getTransactionReference());
        }

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .disputeId("DSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase())
                .transactionReference(transaction.getTransactionReference())
                .accountNumber(account.getAccountNumber())
                .customerUsername(account.getOwnerUsername())
                .disputedAmount(request.getDisputedAmount())
                .currency(transaction.getCurrency())
                .reasonCode(request.getReasonCode())
                .description(request.getDescription())
                .status(DisputeStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build());
        recordEvent(dispute, null, DisputeStatus.OPEN, actor, "Dispute logged");

        notifyCustomer(account, "Dispute " + dispute.getDisputeId() + " received",
                "We have received your dispute " + dispute.getDisputeId() + " and will review it shortly.");
        return disputeMapper.toResponse(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> getDisputes(DisputeStatus status, String accountNumber, int page, int size, Actor actor) {
        Specification<Dispute> spec = Specification.where(null);
        if (!actor.agent()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customerUsername"), actor.username()));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (accountNumber != null && !accountNumber.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("accountNumber"), accountNumber));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(disputeRepository.findAll(spec, pageable).map(disputeMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse getDispute(String disputeId, Actor actor) {
        Dispute dispute = findAuthorised(disputeId, actor);
        return disputeMapper.toResponse(dispute, eventRepository.findByDisputeIdOrderByCreatedAtAscIdAsc(disputeId));
    }

    @Override
    @Transactional
    public DisputeResponse cancelDispute(String disputeId, Actor actor) {
        Dispute dispute = findAuthorised(disputeId, actor);
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new ConflictException("Only OPEN disputes can be cancelled; current status is " + dispute.getStatus());
        }
        transition(dispute, DisputeStatus.CANCELLED, actor, "Cancelled by " + (actor.agent() ? "agent" : "customer"));
        return disputeMapper.toResponse(dispute);
    }

    @Override
    @Transactional
    public DisputeResponse updateStatus(String disputeId, DisputeStatusUpdateRequest request, Actor actor) {
        if (!actor.agent()) {
            throw new AccessDeniedException("Only agents can update dispute status");
        }
        DisputeStatus target = request.getStatus();
        if (!AGENT_TARGETS.contains(target)) {
            throw new IllegalArgumentException("Status must be one of " + AGENT_TARGETS);
        }
        boolean closing = target == DisputeStatus.RESOLVED || target == DisputeStatus.REJECTED;
        if (closing && (request.getNote() == null || request.getNote().isBlank())) {
            throw new IllegalArgumentException("A note is required when resolving or rejecting a dispute");
        }

        Dispute dispute = disputeRepository.findByDisputeId(disputeId).orElseThrow(() -> notFound(disputeId));
        if (!dispute.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Cannot move dispute from " + dispute.getStatus() + " to " + target);
        }

        Account account = accountRepository.findByAccountNumber(dispute.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + dispute.getAccountNumber()));

        if (closing) {
            dispute.setResolutionNote(request.getNote());
            dispute.setResolvedAt(LocalDateTime.now());
        }
        if (target == DisputeStatus.RESOLVED) {
            refund(dispute, account);
        }
        transition(dispute, target, actor, request.getNote());

        notifyCustomer(account, "Dispute " + disputeId + " update",
                "Your dispute " + disputeId + " is now " + target + ".");
        return disputeMapper.toResponse(dispute);
    }

    // Core banking posts the reversal in production; mirrored locally so balances stay consistent.
    private void refund(Dispute dispute, Account account) {
        LocalDateTime now = LocalDateTime.now();
        transactionRepository.save(Transaction.builder()
                .transactionReference("REV-" + dispute.getDisputeId())
                .accountNumber(account.getAccountNumber())
                .amount(dispute.getDisputedAmount())
                .currency(dispute.getCurrency())
                .type(TransactionType.REVERSAL)
                .merchantName("Dispute refund")
                .status(TransactionStatus.POSTED)
                .transactionDate(now)
                .description("Refund for dispute " + dispute.getDisputeId())
                .build());
        account.setAvailableBalance(account.getAvailableBalance().add(dispute.getDisputedAmount()));
        accountRepository.save(account);
    }

    private void transition(Dispute dispute, DisputeStatus target, Actor actor, String note) {
        DisputeStatus from = dispute.getStatus();
        dispute.setStatus(target);
        dispute.setUpdatedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        recordEvent(dispute, from, target, actor, note);
    }

    private void recordEvent(Dispute dispute, DisputeStatus from, DisputeStatus to, Actor actor, String note) {
        eventRepository.save(DisputeEvent.builder()
                .disputeId(dispute.getDisputeId())
                .fromStatus(from)
                .toStatus(to)
                .actor(actor.username())
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Dispute findAuthorised(String disputeId, Actor actor) {
        return disputeRepository.findByDisputeId(disputeId)
                .filter(d -> actor.agent() || d.getCustomerUsername().equals(actor.username()))
                .orElseThrow(() -> notFound(disputeId));
    }

    // Notification failures must not roll back the dispute.
    private void notifyCustomer(Account account, String subject, String message) {
        try {
            emailClient.sendEmail(account.getEmail(), subject, message);
            smsClient.sendSms(account.getMobileNumber(), message);
        } catch (RuntimeException ex) {
            log.warn("Failed to send dispute notification: {}", ex.getMessage());
        }
    }

    private ResourceNotFoundException notFound(String disputeId) {
        return new ResourceNotFoundException("Dispute not found: " + disputeId);
    }
}
