package za.co.transactiondispute.service;

import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Dispute;

import java.util.List;
import java.util.Optional;

public interface DisputeService {
    List<Dispute> getAllDisputes();
    Optional<Dispute> getDisputeById(Long id);
    Dispute createDispute(DisputeCreateRequest request);
    Dispute createDispute(DisputeCreateRequest request, String idempotencyKey);
    Dispute reverseDispute(Long disputeId, String reasonCode);
    Dispute reverseDispute(Long disputeId, String reasonCode, String idempotencyKey);
}
