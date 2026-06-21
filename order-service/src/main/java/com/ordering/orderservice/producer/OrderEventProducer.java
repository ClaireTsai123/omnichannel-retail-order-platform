package com.ordering.orderservice.producer;

import com.ordering.common.event.OrderEvent;
import com.ordering.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publish(OrderEvent event) {
        kafkaTemplate.send(
                KafkaTopics.ORDER_EVENTS_TOPIC,
                event.getOrderId().toString(),
                event
        );
    }
}
