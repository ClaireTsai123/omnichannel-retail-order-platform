package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.fulfillmentservice.entity.Fulfillment;
import com.ordering.fulfillmentservice.metrics.FulfillmentMetrics;
import com.ordering.fulfillmentservice.producer.FulfillmentEventProducer;
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
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, producer);

        FulfillmentResponse first = service.createFulfillment(1001L, 501L, "F-1001-1");
        FulfillmentResponse second = service.createFulfillment(1001L, 501L, "F-1001-2");
        FulfillmentResponse duplicate = service.createFulfillment(1001L, 501L, "F-1001-1");

        assertThat(service.getByOrderId(1001L)).hasSize(2);
        assertThat(first.getId()).isEqualTo(duplicate.getId());
        assertThat(second.getFulfillmentNo()).isEqualTo("F-1001-2");
        assertThat(producer.events).isEmpty();
    }

    @Test
    void updateStatusUsesFulfillmentIdAndPublishesFulfillmentMetadata() {
        List<Fulfillment> store = new ArrayList<>();
        CapturingFulfillmentEventProducer producer = new CapturingFulfillmentEventProducer();
        FulfillmentServiceImpl service = fulfillmentService(store, producer);
        service.createFulfillment(1001L, 501L, "F-1001-1");
        FulfillmentResponse second = service.createFulfillment(1001L, 501L, "F-1001-2");

        FulfillmentResponse updated = service.updateStatus(second.getId(), FulfillmentStatus.SHIPPED);

        assertThat(updated.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(producer.events).hasSize(1);
        FulfillmentStatusUpdatedEvent event = producer.events.get(0);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getFulfillmentId()).isEqualTo(second.getId());
        assertThat(event.getFulfillmentNo()).isEqualTo("F-1001-2");
        assertThat(event.getOrderId()).isEqualTo(1001L);
        assertThat(event.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(event.getVersion()).isEqualTo(1);
    }

    private FulfillmentServiceImpl fulfillmentService(List<Fulfillment> store,
                                                      CapturingFulfillmentEventProducer producer) {
        return new FulfillmentServiceImpl(
                fulfillmentRepository(store),
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
