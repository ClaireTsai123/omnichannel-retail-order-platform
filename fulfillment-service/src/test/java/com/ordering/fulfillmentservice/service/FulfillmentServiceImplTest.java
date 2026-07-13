package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentLineRequest;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.common.dto.OrderItemDTO;
import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.fulfillmentservice.consumer.OrderEventConsumer;
import com.ordering.fulfillmentservice.entity.Fulfillment;
import com.ordering.fulfillmentservice.entity.FulfillmentLine;
import com.ordering.fulfillmentservice.metrics.FulfillmentMetrics;
import com.ordering.fulfillmentservice.producer.FulfillmentEventProducer;
import com.ordering.fulfillmentservice.repository.FulfillmentLineRepository;
import com.ordering.fulfillmentservice.repository.FulfillmentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentServiceImplTest {

    @Test
    void createsMultipleFulfillmentsForSameOrderWithDifferentFulfillmentNumbers() {
        List<Fulfillment> store = new ArrayList<>();
        List<FulfillmentLine> lines = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, lines, producer);

        FulfillmentResponse first = service.createFulfillment(1001L, 501L, "F-1001-1",
                List.of(lineRequest(9001L, "SKU-A", 2)));
        FulfillmentResponse second = service.createFulfillment(1001L, 501L, "F-1001-2",
                List.of(lineRequest(9002L, "SKU-B", 1)));
        FulfillmentResponse duplicate = service.createFulfillment(1001L, 501L, "F-1001-1",
                List.of(lineRequest(9001L, "SKU-A", 2)));

        assertThat(service.getByOrderId(1001L)).hasSize(2);
        assertThat(first.getId()).isEqualTo(duplicate.getId());
        assertThat(second.getFulfillmentNo()).isEqualTo("F-1001-2");
        assertThat(first.getLines()).hasSize(1);
        assertThat(second.getLines()).hasSize(1);
        assertThat(producer.events).isEmpty();
    }

    @Test
    void updateStatusUsesFulfillmentIdAndPublishesFulfillmentMetadata() {
        List<Fulfillment> store = new ArrayList<>();
        List<FulfillmentLine> lines = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, lines, producer);
        service.createFulfillment(1001L, 501L, "F-1001-1");
        FulfillmentResponse second = service.createFulfillment(1001L, 501L, "F-1001-2",
                List.of(lineRequest(9002L, "SKU-B", 1)));

        FulfillmentResponse updated = service.updateStatus(second.getId(), FulfillmentStatus.SHIPPED);

        assertThat(updated.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(producer.events).hasSize(1);
        FulfillmentStatusUpdatedEvent event = producer.events.get(0);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getFulfillmentId()).isEqualTo(second.getId());
        assertThat(event.getFulfillmentNo()).isEqualTo("F-1001-2");
        assertThat(event.getOrderId()).isEqualTo(1001L);
        assertThat(event.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(event.getLines()).hasSize(1);
        assertThat(event.getLines().get(0).getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(event.getVersion()).isEqualTo(1);
    }

    @Test
    void lineStatusUpdatesDeriveFulfillmentStatusForPartialShipmentAndDelivery() {
        List<Fulfillment> store = new ArrayList<>();
        List<FulfillmentLine> lines = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, lines, producer);
        FulfillmentResponse fulfillment = service.createFulfillment(1001L, 501L, "F-1001-1",
                List.of(
                        lineRequest(9001L, "SKU-A", 2),
                        lineRequest(9002L, "SKU-B", 1)
                ));

        service.updateLineStatus(fulfillment.getId(), fulfillment.getLines().get(0).getId(), FulfillmentStatus.SHIPPED);
        FulfillmentResponse partiallyDelivered = service.updateLineStatus(
                fulfillment.getId(),
                fulfillment.getLines().get(0).getId(),
                FulfillmentStatus.DELIVERED
        );
        FulfillmentResponse fullyDelivered = service.updateLineStatus(
                fulfillment.getId(),
                fulfillment.getLines().get(1).getId(),
                FulfillmentStatus.DELIVERED
        );

        assertThat(partiallyDelivered.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(fullyDelivered.getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(producer.events).hasSize(3);
        assertThat(producer.events.get(0).getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(producer.events.get(2).getLines())
                .allMatch(line -> line.getStatus() == FulfillmentStatus.DELIVERED);
    }

    @Test
    void lateLineStatusDoesNotRegressFulfillmentOrPublishEvent() {
        List<Fulfillment> store = new ArrayList<>();
        List<FulfillmentLine> lines = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, lines, producer);
        FulfillmentResponse fulfillment = service.createFulfillment(1001L, 501L, "F-1001-1",
                List.of(lineRequest(9001L, "SKU-A", 2)));
        service.updateLineStatus(fulfillment.getId(), fulfillment.getLines().get(0).getId(), FulfillmentStatus.DELIVERED);

        FulfillmentResponse result = service.updateLineStatus(
                fulfillment.getId(),
                fulfillment.getLines().get(0).getId(),
                FulfillmentStatus.PROCESSING
        );

        assertThat(result.getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(producer.events).hasSize(1);
    }

    @Test
    void orderPaidEventCreatesDefaultFulfillmentWithOrderItemLines() {
        List<Fulfillment> store = new ArrayList<>();
        List<FulfillmentLine> lines = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, lines, producer);
        OrderEventConsumer consumer = new OrderEventConsumer(service);

        OrderEvent event = new OrderEvent();
        event.setEventType(OrderEventType.ORDER_PAID);
        event.setOrderId(1001L);
        event.setUserId(501L);
        event.setItems(List.of(orderItem(9001L, "SKU-A", 2), orderItem(9002L, "SKU-B", 1)));

        consumer.consume(event);

        List<FulfillmentResponse> fulfillments = service.getByOrderId(1001L);
        assertThat(fulfillments).hasSize(1);
        assertThat(fulfillments.get(0).getFulfillmentNo()).isEqualTo("F-1001-1");
        assertThat(fulfillments.get(0).getLines()).hasSize(2);
        assertThat(fulfillments.get(0).getLines())
                .extracting("orderItemId")
                .containsExactly(9001L, 9002L);
    }

    private FulfillmentServiceImpl fulfillmentService(List<Fulfillment> store,
                                                      List<FulfillmentLine> lines,
                                                      CapturingFulfillmentEventProducer producer) {
        return new FulfillmentServiceImpl(
                fulfillmentRepository(store),
                fulfillmentLineRepository(lines),
                producer,
                new FulfillmentMetrics(new SimpleMeterRegistry())
        );
    }

    private FulfillmentRepository fulfillmentRepository(List<Fulfillment> store) {
        AtomicLong ids = new AtomicLong(1);
        return (FulfillmentRepository) Proxy.newProxyInstance(
                FulfillmentRepository.class.getClassLoader(),
                new Class<?>[]{FulfillmentRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByOrderIdAndFulfillmentNo")) {
                        Long orderId = (Long) args[0];
                        String fulfillmentNo = (String) args[1];
                        return store.stream()
                                .filter(fulfillment -> fulfillment.getOrderId().equals(orderId))
                                .filter(fulfillment -> fulfillment.getFulfillmentNo().equals(fulfillmentNo))
                                .findFirst();
                    }
                    if (method.getName().equals("findByOrderIdOrderByIdAsc")) {
                        Long orderId = (Long) args[0];
                        return store.stream()
                                .filter(fulfillment -> fulfillment.getOrderId().equals(orderId))
                                .sorted(Comparator.comparing(Fulfillment::getId))
                                .toList();
                    }
                    if (method.getName().equals("findById")) {
                        Long id = (Long) args[0];
                        return store.stream()
                                .filter(fulfillment -> fulfillment.getId().equals(id))
                                .findFirst();
                    }
                    if (method.getName().equals("save")) {
                        Fulfillment fulfillment = (Fulfillment) args[0];
                        if (fulfillment.getId() == null) {
                            fulfillment.setId(ids.getAndIncrement());
                            store.add(fulfillment);
                        }
                        return fulfillment;
                    }
                    if (method.getName().equals("toString")) {
                        return "FulfillmentRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private FulfillmentLineRepository fulfillmentLineRepository(List<FulfillmentLine> store) {
        AtomicLong ids = new AtomicLong(1);
        return (FulfillmentLineRepository) Proxy.newProxyInstance(
                FulfillmentLineRepository.class.getClassLoader(),
                new Class<?>[]{FulfillmentLineRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByFulfillmentIdOrderByIdAsc")) {
                        Long fulfillmentId = (Long) args[0];
                        return store.stream()
                                .filter(line -> line.getFulfillmentId().equals(fulfillmentId))
                                .sorted(Comparator.comparing(FulfillmentLine::getId))
                                .toList();
                    }
                    if (method.getName().equals("findByIdAndFulfillmentId")) {
                        Long id = (Long) args[0];
                        Long fulfillmentId = (Long) args[1];
                        return store.stream()
                                .filter(line -> line.getId().equals(id))
                                .filter(line -> line.getFulfillmentId().equals(fulfillmentId))
                                .findFirst();
                    }
                    if (method.getName().equals("save")) {
                        FulfillmentLine line = (FulfillmentLine) args[0];
                        if (line.getId() == null) {
                            line.setId(ids.getAndIncrement());
                            store.add(line);
                        }
                        return line;
                    }
                    if (method.getName().equals("toString")) {
                        return "FulfillmentLineRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private FulfillmentLineRequest lineRequest(Long orderItemId, String sku, Integer quantity) {
        FulfillmentLineRequest request = new FulfillmentLineRequest();
        request.setOrderItemId(orderItemId);
        request.setProductId(orderItemId + 100L);
        request.setSku(sku);
        request.setQuantity(quantity);
        return request;
    }

    private OrderItemDTO orderItem(Long orderItemId, String sku, Integer quantity) {
        OrderItemDTO item = new OrderItemDTO();
        item.setId(orderItemId);
        item.setProductId(orderItemId + 100L);
        item.setSku(sku);
        item.setQuantity(quantity);
        return item;
    }

    private static class CapturingFulfillmentEventProducer extends FulfillmentEventProducer {
        private final List<FulfillmentStatusUpdatedEvent> events = new ArrayList<>();

        CapturingFulfillmentEventProducer() {
            super(null);
        }

        @Override
        public void publishStatusUpdatedEvent(FulfillmentStatusUpdatedEvent event) {
            events.add(event);
        }
    }
}
