package za.co.transactiondispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.config.LocalDateTimeAttributeConverter;
import za.co.transactiondispute.enums.DisputeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "disputes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String disputeId;

    @Column(nullable = false)
    private String transactionReference;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal disputedAmount;

    @Column(nullable = false)
    private String reasonCode;

    @Column(length = 1000)
    private String description;

    @Column(length = 255)
    private String assignedToFullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime createdAt;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private LocalDateTime updatedAt;
}
