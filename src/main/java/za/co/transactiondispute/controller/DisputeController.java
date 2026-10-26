package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.ReverseDisputeRequest;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.service.DisputeService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeService disputeService;
    private final Map<String, DisputeResponse> createIdempotencyCache = new ConcurrentHashMap<>();
    private final Map<String, DisputeResponse> reverseIdempotencyCache = new ConcurrentHashMap<>();

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping("/disputes")
    public Page<DisputeResponse> getDisputes(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        List<DisputeResponse> disputes = disputeService.getAllDisputes().stream()
                .map(this::toResponse)
                .toList();
        return pageFromList(disputes, pageable);
    }

    @GetMapping("/disputes/{id}")
    public ResponseEntity<DisputeResponse> getDispute(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(disputeService.getDisputeById(id).orElseThrow()));
    }

    public ResponseEntity<DisputeResponse> createDispute(@Valid @RequestBody DisputeCreateRequest request) {
        return createDispute(request, "legacy-create-" + java.util.UUID.randomUUID());
    }

    @PostMapping("/disputes")
    public ResponseEntity<DisputeResponse> createDispute(@Valid @RequestBody DisputeCreateRequest request,
                                                       @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        String resolvedKey = validateIdempotencyKey(idempotencyKey, "Dispute creation");
        String cacheKey = "create:" + currentUserKey() + ":" + resolvedKey;
        DisputeResponse cached = createIdempotencyCache.get(cacheKey);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        Dispute dispute = disputeService.createDispute(request, resolvedKey);
        DisputeResponse response = toResponse(dispute);
        DisputeResponse previous = createIdempotencyCache.putIfAbsent(cacheKey, response);
        if (previous != null) {
            return ResponseEntity.ok(previous);
        }

        URI location;
        try {
            location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(dispute.getId())
                    .toUri();
        } catch (IllegalStateException ex) {
            location = URI.create("/api/v1/disputes/" + dispute.getId());
        }
        return ResponseEntity.created(location).body(response);
    }

    public ResponseEntity<DisputeResponse> reverseDispute(@PathVariable Long id, @RequestBody ReverseDisputeRequest request) {
        return reverseDispute(id, request, "legacy-reverse-" + java.util.UUID.randomUUID());
    }

    @PostMapping("/disputes/{id}/reverse")
    public ResponseEntity<DisputeResponse> reverseDispute(@PathVariable Long id,
                                                        @RequestBody ReverseDisputeRequest request,
                                                        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        String resolvedKey = validateIdempotencyKey(idempotencyKey, "Dispute reversal");
        String cacheKey = "reverse:" + currentUserKey() + ":" + id + ":" + resolvedKey;
        DisputeResponse cached = reverseIdempotencyCache.get(cacheKey);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        String reasonCode = request != null ? request.getReasonCode() : null;
        Dispute dispute = disputeService.reverseDispute(id, reasonCode, resolvedKey);
        DisputeResponse response = toResponse(dispute);
        DisputeResponse previous = reverseIdempotencyCache.putIfAbsent(cacheKey, response);
        if (previous != null) {
            return ResponseEntity.ok(previous);
        }
        return ResponseEntity.ok(response);
    }

    private String validateIdempotencyKey(String rawKey, String action) {
        String key = rawKey == null ? "" : rawKey.trim();
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, action + " requires the X-Idempotency-Key header.");
        }
        return key;
    }

    private String currentUserKey() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "anonymous";
    }

    private DisputeResponse toResponse(Dispute dispute) {
        return DisputeResponse.builder()
                .id(dispute.getId())
                .disputeId(dispute.getDisputeId())
                .transactionReference(dispute.getTransactionReference())
                .accountNumber(dispute.getAccountNumber())
                .disputedAmount(dispute.getDisputedAmount())
                .reasonCode(dispute.getReasonCode())
                .disputeType(dispute.getDisputeType())
                .description(dispute.getDescription())
                .assignedToFullName(dispute.getAssignedToFullName())
                .provisionalCreditIssued(dispute.getProvisionalCreditIssued())
                .merchantEvidenceProvided(dispute.getMerchantEvidenceProvided())
                .decisionOutcome(dispute.getDecisionOutcome())
                .status(dispute.getStatus())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }

    private Page<DisputeResponse> pageFromList(List<DisputeResponse> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content, Pageable.unpaged(), content.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), content.size());
        List<DisputeResponse> pageContent = (start >= content.size()) ? List.of() : content.subList(start, end);
        return new PageImpl<>(pageContent, pageable, content.size());
    }
}
