package com.ordering.paymentservice.producer;

import com.ordering.common.event.PaymentEvent;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.paymentservice.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publish(PaymentEvent event) {
        kafkaTemplate.send(
                KafkaTopics.PAYMENT_EVENTS_TOPIC,
                event.getOrderId().toString(),
                event
        );
    }
}
