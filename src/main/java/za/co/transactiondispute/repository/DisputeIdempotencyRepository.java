package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.DisputeIdempotency;

import java.util.Optional;

@Repository
public interface DisputeIdempotencyRepository extends JpaRepository<DisputeIdempotency, Long> {
    Optional<DisputeIdempotency> findByUsernameAndOperationAndIdempotencyKey(String username, String operation, String idempotencyKey);
}
