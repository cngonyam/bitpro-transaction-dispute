package za.co.transactiondispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Long disputeId;

    @Enumerated(EnumType.STRING)
    @Column
    private DisputeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus toStatus;

    @Column(length = 255)
    private String action;

    @Column(length = 255)
    private String reasonCode;

    @Column(length = 255)
    private String actor;

    @Column(length = 255)
    private String actorRole;
    @Column(nullable = false)
    private LocalDateTime changedAt;
}
