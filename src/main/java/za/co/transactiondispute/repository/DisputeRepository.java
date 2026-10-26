package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.Dispute;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    Optional<Dispute> findByDisputeId(String disputeId);
    List<Dispute> findByAccountNumber(String accountNumber);
    Optional<Dispute> findByTransactionReference(String transactionReference);
}
