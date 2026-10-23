package za.co.transactiondispute.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.service.DisputeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping("/disputes")
    public List<DisputeResponse> getDisputes() {
        return disputeService.getAllDisputes().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/disputes/{id}")
    public ResponseEntity<DisputeResponse> getDispute(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(disputeService.getDisputeById(id).orElseThrow()));
    }

    @PostMapping("/disputes")
    public ResponseEntity<DisputeResponse> createDispute(@RequestBody DisputeCreateRequest request) {
        Dispute dispute = disputeService.createDispute(request);
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
}
