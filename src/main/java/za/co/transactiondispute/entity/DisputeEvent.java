package za.co.transactiondispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_events", indexes = @Index(name = "idx_event_dispute", columnList = "disputeId"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String disputeId;

    @Enumerated(EnumType.STRING)
    private DisputeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus toStatus;

    @Column(nullable = false)
    private String actor;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
