package za.co.transactiondispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.config.LocalDateTimeAttributeConverter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "dispute_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispute_idempotency_user_operation_key", columnNames = {"username", "operation", "idempotency_key"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 1024)
    private String requestHash;

    @Column(length = 1024)
    private String responseHash;

    @Column
    private Long disputeId;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime createdAt;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime updatedAt;
}
