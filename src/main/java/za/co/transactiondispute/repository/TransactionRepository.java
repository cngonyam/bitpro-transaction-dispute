package za.co.transactiondispute.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.transactiondispute.entity.Transaction;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionReference(String transactionReference);
    List<Transaction> findAllByTransactionReference(String transactionReference);
    List<Transaction> findByAccountNumber(String accountNumber);
}
