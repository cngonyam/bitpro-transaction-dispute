package za.co.transactiondispute.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.Dispute;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    /** Loads the dispute with a pessimistic write lock (SELECT ... FOR UPDATE where the database supports it). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") Long id);

    Optional<Dispute> findByDisputeId(String disputeId);
    List<Dispute> findByAccountNumber(String accountNumber);
    Optional<Dispute> findByTransactionReference(String transactionReference);
}
