package com.ordering.orderservice.consumer;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FulfillmentEventConsumer {

    private final OrderService orderService;
    @KafkaListener(topics = KafkaTopics.FULFILLMENT_EVENTS_TOPIC,
    groupId = "order-service-group")
    public void consume(FulfillmentStatusUpdatedEvent event) {
       orderService.updateStatus(
                event.getOrderId(),
                mapStatus(event.getStatus())
       );
    }

    private OrderStatus mapStatus(FulfillmentStatus status) {
        return switch (status) {
            case CREATED, PROCESSING -> OrderStatus.PROCESSING;
            case SHIPPED ->     OrderStatus.SHIPPED;
            case DELIVERED -> OrderStatus.DELIVERED;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }
}
