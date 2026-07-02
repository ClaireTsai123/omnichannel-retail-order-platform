package com.ordering.fulfillmentservice.consumer;

import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.fulfillmentservice.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    private final FulfillmentService fulfillmentService;

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS_TOPIC,
    groupId = "fulfillment-service-group")
    public void consume(OrderEvent event) {
        if (event.getEventType() == OrderEventType.ORDER_PAID) {
            fulfillmentService.createFulfillment(
                    event.getOrderId(),
                    event.getUserId()
            );
        }

        if (event.getEventType() == OrderEventType.ORDER_CANCELLED) {
            fulfillmentService.cancelFulfillment(event.getOrderId());
        }
    }
}
