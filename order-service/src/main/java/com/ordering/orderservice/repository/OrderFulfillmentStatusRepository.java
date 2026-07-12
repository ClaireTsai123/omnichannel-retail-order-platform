package com.ordering.orderservice.repository;

import com.ordering.orderservice.entity.OrderFulfillmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderFulfillmentStatusRepository extends JpaRepository<OrderFulfillmentStatus, Long> {
    Optional<OrderFulfillmentStatus> findByFulfillmentId(Long fulfillmentId);

    List<OrderFulfillmentStatus> findByOrderId(Long orderId);
}
