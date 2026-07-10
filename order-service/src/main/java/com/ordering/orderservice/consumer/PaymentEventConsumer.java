package com.ordering.orderservice.consumer;

import com.ordering.common.domain.OrderStatus;
import com.ordering.common.event.PaymentEvent;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.orderservice.client.InventoryClient;
import com.ordering.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final OrderService orderService;
    private final InventoryClient inventoryClient;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS_TOPIC, groupId = "order-service-group")
    public void consumePaymentEvent(PaymentEvent event) {
        System.out.println("Order service received payment event: "
                + event.getEventType()
                + ", orderId=" + event.getOrderId());
        switch (event.getEventType()) {
            case PAYMENT_AUTHORIZED ->
                    System.out.println("PAYMENT_AUTHORIZED acknowledged; payOrder owns the PAID transition, orderId="
                            + event.getOrderId());
            case PAYMENT_FAILED -> {
                inventoryClient.releaseInventory(event.getOrderId());
                orderService.updateStatus(event.getOrderId(), OrderStatus.PAYMENT_FAILED);
            }

        }

    }
}
