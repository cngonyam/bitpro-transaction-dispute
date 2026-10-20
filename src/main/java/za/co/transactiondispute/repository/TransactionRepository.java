package za.co.transactiondispute.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.Transaction;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    @Query("select t from Transaction t where t.accountNumber = :accountNumber "
            + "and t.transactionDate >= :from and t.transactionDate < :to")
    Page<Transaction> findForAccount(@Param("accountNumber") String accountNumber,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     Pageable pageable);
}
