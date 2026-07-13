package com.ordering.orderservice.consumer;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.event.FulfillmentLineEvent;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.orderservice.entity.OrderFulfillmentStatus;
import com.ordering.orderservice.entity.ProcessedKafkaEvent;
import com.ordering.orderservice.repository.OrderFulfillmentStatusRepository;
import com.ordering.orderservice.repository.ProcessedKafkaEventRepository;
import com.ordering.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentEventConsumerTest {

    @Test
    void multiFulfillmentEventsAggregateOrderStatus() {
        Set<String> processedEvents = new HashSet<>();
        Map<Long, OrderFulfillmentStatus> fulfillmentStatuses = new HashMap<>();
        List<OrderStatus> orderUpdates = new ArrayList<>();
        FulfillmentEventConsumer consumer = new FulfillmentEventConsumer(
                orderServiceRecording(orderUpdates),
                processedEventRepository(processedEvents),
                fulfillmentStatusRepository(fulfillmentStatuses)
        );

        consumer.consume(event("event-1", 11L, "F-1001-1", FulfillmentStatus.PROCESSING));
        consumer.consume(event("event-2", 12L, "F-1001-2", FulfillmentStatus.SHIPPED));
        consumer.consume(event("event-3", 11L, "F-1001-1", FulfillmentStatus.DELIVERED));
        consumer.consume(event("event-4", 12L, "F-1001-2", FulfillmentStatus.DELIVERED));

        assertThat(orderUpdates).containsExactly(
                OrderStatus.PROCESSING,
                OrderStatus.SHIPPED,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED
        );
        assertThat(processedEvents).containsExactlyInAnyOrder("event-1", "event-2", "event-3", "event-4");
        assertThat(fulfillmentStatuses.get(12L).getLineCount()).isEqualTo(2);
        assertThat(fulfillmentStatuses.get(12L).getShippedLineCount()).isEqualTo(2);
        assertThat(fulfillmentStatuses.get(12L).getDeliveredLineCount()).isEqualTo(2);
    }

    @Test
    void duplicateFulfillmentEventDoesNotUpdateProjectionOrOrderAgain() {
        Set<String> processedEvents = new HashSet<>();
        processedEvents.add("event-1");
        Map<Long, OrderFulfillmentStatus> fulfillmentStatuses = new HashMap<>();
        List<OrderStatus> orderUpdates = new ArrayList<>();
        FulfillmentEventConsumer consumer = new FulfillmentEventConsumer(
                orderServiceRecording(orderUpdates),
                processedEventRepository(processedEvents),
                fulfillmentStatusRepository(fulfillmentStatuses)
        );

        consumer.consume(event("event-1", 11L, "F-1001-1", FulfillmentStatus.SHIPPED));

        assertThat(orderUpdates).isEmpty();
        assertThat(fulfillmentStatuses).isEmpty();
    }

    @Test
    void lateRegressiveFulfillmentEventIsMarkedProcessedButDoesNotMoveOrderBackward() {
        Set<String> processedEvents = new HashSet<>();
        Map<Long, OrderFulfillmentStatus> fulfillmentStatuses = new HashMap<>();
        OrderFulfillmentStatus delivered = new OrderFulfillmentStatus();
        delivered.setFulfillmentId(11L);
        delivered.setFulfillmentNo("F-1001-1");
        delivered.setOrderId(1001L);
        delivered.setStatus(FulfillmentStatus.DELIVERED);
        fulfillmentStatuses.put(11L, delivered);
        List<OrderStatus> orderUpdates = new ArrayList<>();
        FulfillmentEventConsumer consumer = new FulfillmentEventConsumer(
                orderServiceRecording(orderUpdates),
                processedEventRepository(processedEvents),
                fulfillmentStatusRepository(fulfillmentStatuses)
        );

        consumer.consume(event("late-event", 11L, "F-1001-1", FulfillmentStatus.PROCESSING));

        assertThat(orderUpdates).isEmpty();
        assertThat(fulfillmentStatuses.get(11L).getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(processedEvents).containsExactly("late-event");
    }

    private FulfillmentStatusUpdatedEvent event(String eventId,
                                                Long fulfillmentId,
                                                String fulfillmentNo,
                                                FulfillmentStatus status) {
        FulfillmentStatusUpdatedEvent event = new FulfillmentStatusUpdatedEvent();
        event.setEventId(eventId);
        event.setFulfillmentId(fulfillmentId);
        event.setFulfillmentNo(fulfillmentNo);
        event.setOrderId(1001L);
        event.setStatus(status);
        event.setLines(List.of(line(1L, status), line(2L, status)));
        return event;
    }

    private FulfillmentLineEvent line(Long orderItemId, FulfillmentStatus status) {
        FulfillmentLineEvent event = new FulfillmentLineEvent();
        event.setLineId(orderItemId + 100L);
        event.setOrderItemId(orderItemId);
        event.setSku("SKU-" + orderItemId);
        event.setQuantity(1);
        event.setStatus(status);
        return event;
    }

    private OrderService orderServiceRecording(List<OrderStatus> orderUpdates) {
        return (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateStatus") && args.length == 5) {
                        orderUpdates.add((OrderStatus) args[1]);
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderService test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ProcessedKafkaEventRepository processedEventRepository(Set<String> processedEvents) {
        return (ProcessedKafkaEventRepository) Proxy.newProxyInstance(
                ProcessedKafkaEventRepository.class.getClassLoader(),
                new Class<?>[]{ProcessedKafkaEventRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsByEventId")) {
                        return processedEvents.contains((String) args[0]);
                    }
                    if (method.getName().equals("save")) {
                        processedEvents.add(((ProcessedKafkaEvent) args[0]).getEventId());
                        return args[0];
                    }
                    if (method.getName().equals("toString")) {
                        return "ProcessedKafkaEventRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private OrderFulfillmentStatusRepository fulfillmentStatusRepository(
            Map<Long, OrderFulfillmentStatus> fulfillmentStatuses
    ) {
        return (OrderFulfillmentStatusRepository) Proxy.newProxyInstance(
                OrderFulfillmentStatusRepository.class.getClassLoader(),
                new Class<?>[]{OrderFulfillmentStatusRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByFulfillmentId")) {
                        return Optional.ofNullable(fulfillmentStatuses.get((Long) args[0]));
                    }
                    if (method.getName().equals("findByOrderId")) {
                        Long orderId = (Long) args[0];
                        return fulfillmentStatuses.values().stream()
                                .filter(status -> status.getOrderId().equals(orderId))
                                .toList();
                    }
                    if (method.getName().equals("save")) {
                        OrderFulfillmentStatus status = (OrderFulfillmentStatus) args[0];
                        fulfillmentStatuses.put(status.getFulfillmentId(), status);
                        return status;
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderFulfillmentStatusRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
