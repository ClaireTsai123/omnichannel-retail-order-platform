package com.ordering.orderservice.repository;

import com.ordering.orderservice.entity.ProcessedKafkaEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, Long> {
    boolean existsByEventId(String eventId);
}
