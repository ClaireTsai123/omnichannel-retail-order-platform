package com.ordering.orderservice.consumer;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.event.FulfillmentLineEvent;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.orderservice.entity.OrderFulfillmentStatus;
import com.ordering.orderservice.entity.ProcessedKafkaEvent;
import com.ordering.orderservice.repository.OrderFulfillmentStatusRepository;
import com.ordering.orderservice.repository.ProcessedKafkaEventRepository;
import com.ordering.orderservice.service.OrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FulfillmentEventConsumer {

    private final OrderService orderService;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final OrderFulfillmentStatusRepository orderFulfillmentStatusRepository;

    @KafkaListener(topics = KafkaTopics.FULFILLMENT_EVENTS_TOPIC,
    groupId = "order-service-group")
    @Transactional
    public void consume(FulfillmentStatusUpdatedEvent event) {
        String eventId = eventId(event);
        if (processedKafkaEventRepository.existsByEventId(eventId)) {
            System.out.println("Skipping duplicate fulfillment event, eventId=" + eventId);
            return;
        }

        if (!upsertFulfillmentStatus(event)) {
            markProcessed(eventId, event);
            return;
        }

        orderService.updateStatus(
                 event.getOrderId(),
                 deriveOrderStatus(event.getOrderId()),
                 "FULFILLMENT_AGGREGATED_" + event.getStatus(),
                 "FULFILLMENT_EVENT",
                 eventId
        );
        markProcessed(eventId, event);
    }

    private boolean upsertFulfillmentStatus(FulfillmentStatusUpdatedEvent event) {
        Long fulfillmentId = fulfillmentId(event);
        OrderFulfillmentStatus status = orderFulfillmentStatusRepository.findByFulfillmentId(fulfillmentId)
                .orElseGet(OrderFulfillmentStatus::new);

        if (status.getStatus() != null && isLateOrRegressive(status.getStatus(), event.getStatus())) {
            System.out.println("Skipping late fulfillment event, fulfillmentId="
                    + fulfillmentId
                    + ", currentStatus="
                    + status.getStatus()
                    + ", eventStatus="
                    + event.getStatus());
            return false;
        }

        status.setFulfillmentId(fulfillmentId);
        status.setFulfillmentNo(event.getFulfillmentNo());
        status.setOrderId(event.getOrderId());
        status.setStatus(event.getStatus());
        status.setLineCount(lineCount(event));
        status.setShippedLineCount(shippedLineCount(event));
        status.setDeliveredLineCount(deliveredLineCount(event));
        orderFulfillmentStatusRepository.save(status);
        return true;
    }

    private int lineCount(FulfillmentStatusUpdatedEvent event) {
        return event.getLines() == null ? 0 : event.getLines().size();
    }

    private int shippedLineCount(FulfillmentStatusUpdatedEvent event) {
        if (event.getLines() == null) {
            return 0;
        }
        return (int) event.getLines().stream()
                .map(FulfillmentLineEvent::getStatus)
                .filter(status -> status == FulfillmentStatus.SHIPPED || status == FulfillmentStatus.DELIVERED)
                .count();
    }

    private int deliveredLineCount(FulfillmentStatusUpdatedEvent event) {
        if (event.getLines() == null) {
            return 0;
        }
        return (int) event.getLines().stream()
                .map(FulfillmentLineEvent::getStatus)
                .filter(status -> status == FulfillmentStatus.DELIVERED)
                .count();
    }

    private boolean isLateOrRegressive(FulfillmentStatus currentStatus, FulfillmentStatus incomingStatus) {
        if (currentStatus == FulfillmentStatus.CANCELLED || currentStatus == FulfillmentStatus.DELIVERED) {
            return currentStatus != incomingStatus;
        }
        return fulfillmentRank(incomingStatus) < fulfillmentRank(currentStatus);
    }

    private int fulfillmentRank(FulfillmentStatus status) {
        return switch (status) {
            case CREATED -> 1;
            case PROCESSING -> 2;
            case SHIPPED -> 3;
            case DELIVERED, CANCELLED -> 4;
        };
    }

    private OrderStatus deriveOrderStatus(Long orderId) {
        List<FulfillmentStatus> statuses = orderFulfillmentStatusRepository.findByOrderId(orderId)
                .stream()
                .map(OrderFulfillmentStatus::getStatus)
                .toList();

        if (statuses.isEmpty()) {
            return OrderStatus.PROCESSING;
        }
        if (statuses.stream().allMatch(status -> status == FulfillmentStatus.CANCELLED)) {
            return OrderStatus.CANCELLED;
        }
        if (statuses.stream().allMatch(status -> status == FulfillmentStatus.DELIVERED
                || status == FulfillmentStatus.CANCELLED)) {
            return OrderStatus.DELIVERED;
        }
        if (statuses.stream().anyMatch(status -> status == FulfillmentStatus.SHIPPED
                || status == FulfillmentStatus.DELIVERED)) {
            return OrderStatus.SHIPPED;
        }
        return OrderStatus.PROCESSING;
    }

    private String eventId(FulfillmentStatusUpdatedEvent event) {
        return Optional.ofNullable(event.getEventId())
                .orElse("fulfillment-events:"
                        + fulfillmentId(event)
                        + ":"
                        + event.getStatus()
                        + ":"
                        + event.getOrderId());
    }

    private Long fulfillmentId(FulfillmentStatusUpdatedEvent event) {
        return Optional.ofNullable(event.getFulfillmentId())
                .orElse(-event.getOrderId());
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
