package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderStatus;
import com.ordering.orderservice.client.InventoryClient;
import com.ordering.orderservice.entity.Order;
import com.ordering.orderservice.entity.OrderStatusHistory;
import com.ordering.orderservice.repository.OrderRepository;
import com.ordering.orderservice.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceImplTest {

    @Test
    void handlePaymentFailedReleasesInventoryAndMarksCreatedOrderPaymentFailed() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = orderService(order, releaseCalls, history);

        Order result = orderService.handlePaymentFailed(1001L, "payment-event-1", "PAYMENT_EVENT");

        assertThat(releaseCalls).hasValue(1);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.get(0).getNewStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(history.get(0).getEventId()).isEqualTo("payment-event-1");
    }

    @Test
    void handlePaymentFailedIsIdempotentForAlreadyFailedOrder() {
        Order order = orderWithStatus(OrderStatus.PAYMENT_FAILED);
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = orderService(order, releaseCalls, history);

        Order result = orderService.handlePaymentFailed(1001L);

        assertThat(releaseCalls).hasValue(0);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(history).isEmpty();
    }

    @Test
    void handlePaymentFailedDoesNotReleaseInventoryForPaidOrder() {
        Order order = orderWithStatus(OrderStatus.PAID);
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = orderService(order, releaseCalls, history);

        Order result = orderService.handlePaymentFailed(1001L);

        assertThat(releaseCalls).hasValue(0);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(history).isEmpty();
    }

    @Test
    void updateStatusRecordsAuditHistoryForFulfillmentEvent() {
        Order order = orderWithStatus(OrderStatus.PAID);
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = orderService(order, releaseCalls, history);

        orderService.updateStatus(1001L, OrderStatus.PROCESSING,
                "FULFILLMENT_PROCESSING", "FULFILLMENT_EVENT", "fulfillment-event-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(history.get(0).getNewStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(history.get(0).getEventId()).isEqualTo("fulfillment-event-1");
    }

    @Test
    void updateStatusIgnoresInvalidLateFulfillmentEvent() {
        Order order = orderWithStatus(OrderStatus.DELIVERED);
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = orderService(order, releaseCalls, history);

        orderService.updateStatus(1001L, OrderStatus.PROCESSING,
                "FULFILLMENT_PROCESSING", "FULFILLMENT_EVENT", "late-event-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(history).isEmpty();
    }

    private OrderServiceImpl orderService(Order order,
                                          AtomicInteger releaseCalls,
                                          List<OrderStatusHistory> history) {
        return new OrderServiceImpl(
                repositoryReturning(order),
                null,
                null,
                releaseTrackingInventory(releaseCalls),
                null,
                null,
                null,
                null,
                historyRepository(history),
                null
        );
    }

    private OrderRepository repositoryReturning(Order order) {
        return (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class<?>[]{OrderRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        return Optional.of(order);
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private OrderStatusHistoryRepository historyRepository(List<OrderStatusHistory> history) {
        return (OrderStatusHistoryRepository) Proxy.newProxyInstance(
                OrderStatusHistoryRepository.class.getClassLoader(),
                new Class<?>[]{OrderStatusHistoryRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) {
                        history.add((OrderStatusHistory) args[0]);
                        return args[0];
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderStatusHistoryRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private InventoryClient releaseTrackingInventory(AtomicInteger releaseCalls) {
        return (InventoryClient) Proxy.newProxyInstance(
                InventoryClient.class.getClassLoader(),
                new Class<?>[]{InventoryClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("releaseInventory")) {
                        releaseCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "InventoryClient test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order();
        order.setId(1001L);
        order.setStatus(status);
        return order;
    }
}
