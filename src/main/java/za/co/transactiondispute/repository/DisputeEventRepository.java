package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.DisputeEvent;

import java.util.List;

@Repository
public interface DisputeEventRepository extends JpaRepository<DisputeEvent, Long> {
    List<DisputeEvent> findByDisputeIdOrderByCreatedAtAscIdAsc(String disputeId);
}
