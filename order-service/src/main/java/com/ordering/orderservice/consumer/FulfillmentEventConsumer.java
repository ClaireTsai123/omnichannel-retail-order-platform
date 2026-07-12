package com.ordering.orderservice.consumer;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.orderservice.entity.ProcessedKafkaEvent;
import com.ordering.orderservice.repository.ProcessedKafkaEventRepository;
import com.ordering.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FulfillmentEventConsumer {

    private final OrderService orderService;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;

    @KafkaListener(topics = KafkaTopics.FULFILLMENT_EVENTS_TOPIC,
    groupId = "order-service-group")
    public void consume(FulfillmentStatusUpdatedEvent event) {
        String eventId = eventId(event);
        if (processedKafkaEventRepository.existsByEventId(eventId)) {
            System.out.println("Skipping duplicate fulfillment event, eventId=" + eventId);
            return;
        }
       orderService.updateStatus(
                event.getOrderId(),
                mapStatus(event.getStatus()),
                "FULFILLMENT_" + event.getStatus(),
                "FULFILLMENT_EVENT",
                eventId
       );
       markProcessed(eventId, event);
    }

    private OrderStatus mapStatus(FulfillmentStatus status) {
        return switch (status) {
            case CREATED, PROCESSING -> OrderStatus.PROCESSING;
            case SHIPPED ->     OrderStatus.SHIPPED;
            case DELIVERED -> OrderStatus.DELIVERED;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }

    private String eventId(FulfillmentStatusUpdatedEvent event) {
        return Optional.ofNullable(event.getEventId())
                .orElse("fulfillment-events:"
                        + event.getStatus()
                        + ":"
                        + event.getOrderId());
    }

    private void markProcessed(String eventId, FulfillmentStatusUpdatedEvent event) {
        ProcessedKafkaEvent processed = new ProcessedKafkaEvent();
        processed.setEventId(eventId);
        processed.setTopic(KafkaTopics.FULFILLMENT_EVENTS_TOPIC);
        processed.setEventType(event.getStatus().name());
        processed.setOrderId(event.getOrderId());
        processedKafkaEventRepository.save(processed);
    }
}
