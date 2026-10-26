package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.ReverseDisputeRequest;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.service.DisputeService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeService disputeService;

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

    @PostMapping("/disputes")
    public ResponseEntity<DisputeResponse> createDispute(@Valid @RequestBody DisputeCreateRequest request) {
        Dispute dispute = disputeService.createDispute(request);
        URI location;
        try {
            location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(dispute.getId())
                    .toUri();
        } catch (IllegalStateException ex) {
            location = URI.create("/api/v1/disputes/" + dispute.getId());
        }
        return ResponseEntity.created(location).body(toResponse(dispute));
    }

    @PostMapping("/disputes/{id}/reverse")
    public ResponseEntity<DisputeResponse> reverseDispute(@PathVariable Long id, @RequestBody ReverseDisputeRequest request) {
        String reasonCode = request != null ? request.getReasonCode() : null;
        Dispute dispute = disputeService.reverseDispute(id, reasonCode);
        return ResponseEntity.ok(toResponse(dispute));
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
