package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.DisputeStatusHistory;

import java.util.List;

@Repository
public interface DisputeStatusHistoryRepository extends JpaRepository<DisputeStatusHistory, Long> {
    List<DisputeStatusHistory> findByDisputeIdOrderByChangedAtAsc(Long disputeId);
}
