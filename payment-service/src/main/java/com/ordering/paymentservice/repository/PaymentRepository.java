package com.ordering.paymentservice.repository;

import com.ordering.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
