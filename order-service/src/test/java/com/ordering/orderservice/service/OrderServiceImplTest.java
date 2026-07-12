package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderStatus;
import com.ordering.orderservice.client.InventoryClient;
import com.ordering.orderservice.entity.Order;
import com.ordering.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceImplTest {

    @Test
    void handlePaymentFailedReleasesInventoryAndMarksCreatedOrderPaymentFailed() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        AtomicInteger releaseCalls = new AtomicInteger();
        OrderServiceImpl orderService = orderService(order, releaseCalls);

        Order result = orderService.handlePaymentFailed(1001L);

        assertThat(releaseCalls).hasValue(1);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void handlePaymentFailedIsIdempotentForAlreadyFailedOrder() {
        Order order = orderWithStatus(OrderStatus.PAYMENT_FAILED);
        AtomicInteger releaseCalls = new AtomicInteger();
        OrderServiceImpl orderService = orderService(order, releaseCalls);

        Order result = orderService.handlePaymentFailed(1001L);

        assertThat(releaseCalls).hasValue(0);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void handlePaymentFailedDoesNotReleaseInventoryForPaidOrder() {
        Order order = orderWithStatus(OrderStatus.PAID);
        AtomicInteger releaseCalls = new AtomicInteger();
        OrderServiceImpl orderService = orderService(order, releaseCalls);

        Order result = orderService.handlePaymentFailed(1001L);

        assertThat(releaseCalls).hasValue(0);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private OrderServiceImpl orderService(Order order, AtomicInteger releaseCalls) {
        return new OrderServiceImpl(
                repositoryReturning(order),
                null,
                null,
                releaseTrackingInventory(releaseCalls),
                null,
                null,
                null,
                null,
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
