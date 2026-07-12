package com.ordering.orderservice.consumer;

import com.ordering.common.event.PaymentEvent;
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
public class PaymentEventConsumer {
    private final OrderService orderService;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS_TOPIC, groupId = "order-service-group")
    public void consumePaymentEvent(PaymentEvent event) {
        String eventId = eventId(event);
        if (processedKafkaEventRepository.existsByEventId(eventId)) {
            System.out.println("Skipping duplicate payment event, eventId=" + eventId);
            return;
        }
        System.out.println("Order service received payment event: "
                + event.getEventType()
                + ", orderId=" + event.getOrderId());
        switch (event.getEventType()) {
            case PAYMENT_AUTHORIZED ->
                    System.out.println("PAYMENT_AUTHORIZED acknowledged; payOrder owns the PAID transition, orderId="
                            + event.getOrderId());
            case PAYMENT_FAILED -> orderService.handlePaymentFailed(
                    event.getOrderId(),
                    eventId,
                    "PAYMENT_EVENT"
            );

        }
        markProcessed(eventId, event);

    }

    private String eventId(PaymentEvent event) {
        return Optional.ofNullable(event.getEventId())
                .orElse("payment-events:"
                        + event.getEventType()
                        + ":"
                        + event.getOrderId()
                        + ":"
                        + event.getPaymentId());
    }

    private void markProcessed(String eventId, PaymentEvent event) {
        ProcessedKafkaEvent processed = new ProcessedKafkaEvent();
        processed.setEventId(eventId);
        processed.setTopic(KafkaTopics.PAYMENT_EVENTS_TOPIC);
        processed.setEventType(event.getEventType().name());
        processed.setOrderId(event.getOrderId());
        processedKafkaEventRepository.save(processed);
    }
}
