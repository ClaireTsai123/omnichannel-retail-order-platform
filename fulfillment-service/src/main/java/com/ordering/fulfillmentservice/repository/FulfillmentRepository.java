package com.ordering.fulfillmentservice.repository;

import com.ordering.fulfillmentservice.entity.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, Long> {

    Optional<Fulfillment> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
}
