package com.ordering.fulfillmentservice.repository;

import com.ordering.fulfillmentservice.entity.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, Long> {

    List<Fulfillment> findByOrderIdOrderByIdAsc(Long orderId);
    Optional<Fulfillment> findByOrderIdAndFulfillmentNo(Long orderId, String fulfillmentNo);
    boolean existsByOrderIdAndFulfillmentNo(Long orderId, String fulfillmentNo);
}
