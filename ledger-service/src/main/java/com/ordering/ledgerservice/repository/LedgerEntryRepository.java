package com.ordering.ledgerservice.repository;

import com.ordering.ledgerservice.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByTransactionId(String transactionId);
    List<LedgerEntry> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
