package com.ordering.fulfillmentservice.consumer;

import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.common.dto.FulfillmentLineRequest;
import com.ordering.common.dto.OrderItemDTO;
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
                    event.getUserId(),
                    null,
                    event.getItems() == null ? java.util.List.of() : event.getItems().stream()
                            .map(this::toFulfillmentLineRequest)
                            .toList()
            );
        }

        if (event.getEventType() == OrderEventType.ORDER_CANCELLED) {
            fulfillmentService.cancelFulfillment(event.getOrderId());
        }
    }

    private FulfillmentLineRequest toFulfillmentLineRequest(OrderItemDTO item) {
        FulfillmentLineRequest line = new FulfillmentLineRequest();
        line.setOrderItemId(item.getId());
        line.setProductId(item.getProductId());
        line.setSku(item.getSku());
        line.setQuantity(item.getQuantity());
        return line;
    }
}
