package za.co.transactiondispute.service;

import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeStatusHistory;

import java.util.List;
import java.util.Optional;

public interface DisputeService {
    List<Dispute> getAllDisputes();
    Optional<Dispute> getDisputeById(Long id);
    Dispute createDispute(DisputeCreateRequest request);
    Dispute createDispute(DisputeCreateRequest request, String idempotencyKey);
    Dispute reviewDispute(Long disputeId, String decision, String reviewNote);
    Dispute approveDispute(Long disputeId, String approvalNote);
    Dispute reverseDispute(Long disputeId, String reasonCode);
    Dispute reverseDispute(Long disputeId, String reasonCode, String idempotencyKey);
    List<DisputeStatusHistory> getDisputeHistory(Long disputeId);
}
