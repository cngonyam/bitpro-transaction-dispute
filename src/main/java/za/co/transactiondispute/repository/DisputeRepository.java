package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.enums.DisputeStatus;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long>, JpaSpecificationExecutor<Dispute> {
    Optional<Dispute> findByDisputeId(String disputeId);

    boolean existsByTransactionReferenceAndStatusIn(String transactionReference, Collection<DisputeStatus> statuses);
}
