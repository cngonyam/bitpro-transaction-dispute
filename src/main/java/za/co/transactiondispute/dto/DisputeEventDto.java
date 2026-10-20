package za.co.transactiondispute.dto;

import za.co.transactiondispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record DisputeEventDto(
        DisputeStatus fromStatus,
        DisputeStatus toStatus,
        String actor,
        String note,
        LocalDateTime createdAt) {
}
