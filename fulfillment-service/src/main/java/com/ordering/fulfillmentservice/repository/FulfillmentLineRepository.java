package com.ordering.fulfillmentservice.repository;

import com.ordering.fulfillmentservice.entity.FulfillmentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FulfillmentLineRepository extends JpaRepository<FulfillmentLine, Long> {
    List<FulfillmentLine> findByFulfillmentIdOrderByIdAsc(Long fulfillmentId);

    List<FulfillmentLine> findByOrderId(Long orderId);

    Optional<FulfillmentLine> findByIdAndFulfillmentId(Long id, Long fulfillmentId);
}
