package za.co.transactiondispute.mapper;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.DisputeEventDto;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.DisputeEvent;

import java.util.List;

@Component
public class DisputeMapper {

    public DisputeResponse toResponse(Dispute d) {
        return toResponse(d, null);
    }

    public DisputeResponse toResponse(Dispute d, List<DisputeEvent> history) {
        return DisputeResponse.builder()
                .disputeId(d.getDisputeId())
                .transactionReference(d.getTransactionReference())
                .accountNumber(d.getAccountNumber())
                .disputedAmount(d.getDisputedAmount())
                .currency(d.getCurrency())
                .reasonCode(d.getReasonCode())
                .description(d.getDescription())
                .status(d.getStatus())
                .resolutionNote(d.getResolutionNote())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .resolvedAt(d.getResolvedAt())
                .history(history == null ? null : history.stream()
                        .map(e -> new DisputeEventDto(e.getFromStatus(), e.getToStatus(), e.getActor(), e.getNote(), e.getCreatedAt()))
                        .toList())
                .build();
    }
}
