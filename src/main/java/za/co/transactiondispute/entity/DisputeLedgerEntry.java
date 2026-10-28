package za.co.transactiondispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.config.LocalDateTimeAttributeConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Long disputeId;

    @Column(nullable = false)
    private String transactionReference;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 255)
    private String actor;

    @Column(length = 255)
    private String actorRole;

    @Column(length = 1000)
    private String description;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime createdAt;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime updatedAt;
}
