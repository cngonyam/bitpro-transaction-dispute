package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.DisputeLedgerEntry;

import java.util.List;

@Repository
public interface DisputeLedgerEntryRepository extends JpaRepository<DisputeLedgerEntry, Long> {
    List<DisputeLedgerEntry> findByDisputeIdOrderByCreatedAtAsc(Long disputeId);
    boolean existsByDisputeIdAndAction(Long disputeId, String action);
}
