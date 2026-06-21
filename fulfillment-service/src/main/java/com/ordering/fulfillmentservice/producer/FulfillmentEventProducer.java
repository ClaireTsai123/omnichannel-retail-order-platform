package com.ordering.fulfillmentservice.producer;

import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FulfillmentEventProducer {
    private final KafkaTemplate<String, FulfillmentStatusUpdatedEvent> kafkaTemplate;

    public void publishStatusUpdatedEvent(FulfillmentStatusUpdatedEvent event) {
        kafkaTemplate.send(
                KafkaTopics.FULFILLMENT_EVENTS_TOPIC,
                event.getOrderId().toString(),
                event
        );
    }
}
