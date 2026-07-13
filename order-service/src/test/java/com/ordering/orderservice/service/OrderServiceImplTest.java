package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.domain.PaymentStatus;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.CartDTO;
import com.ordering.common.dto.CartItemDTO;
import com.ordering.common.dto.CreateOrderRequest;
import com.ordering.common.dto.PaymentRequest;
import com.ordering.common.dto.PaymentResponse;
import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.common.exception.ConflictException;
import com.ordering.common.exception.GlobalExceptionHandler;
import com.ordering.common.exception.BadRequestException;
import com.ordering.orderservice.client.CartClient;
import com.ordering.orderservice.client.InventoryClient;
import com.ordering.orderservice.client.PaymentClient;
import com.ordering.orderservice.entity.Order;
import com.ordering.orderservice.entity.OrderItem;
import com.ordering.orderservice.entity.OrderStatusHistory;
import com.ordering.orderservice.metrics.OrderMetrics;
import com.ordering.orderservice.producer.OrderEventProducer;
import com.ordering.orderservice.repository.OrderItemRepository;
import com.ordering.orderservice.repository.OrderRepository;
import com.ordering.orderservice.repository.OrderStatusHistoryRepository;
import com.ordering.orderservice.util.IdGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceImplTest {
    private ExecutorService executorService;
    private final AtomicInteger remoteCallsInsideTransaction = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void createOrderWithNewIdempotencyKeyCreatesOneOrder() {
        IdempotentOrderRepository repository = new IdempotentOrderRepository(false);
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger clearCartCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        OrderServiceImpl orderService = orderServiceForCreate(
                repository,
                reserveCalls,
                clearCartCalls,
                publishedEvents
        );

        Order result = orderService.createOrder(createOrderRequest("checkout-1"));

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getIdempotencyKey()).isEqualTo("checkout-1");
        assertThat(repository.createdOrderCount()).isEqualTo(1);
        assertThat(reserveCalls).hasValue(1);
        assertThat(publishedEvents).hasValue(1);
        assertThat(clearCartCalls).hasValue(1);
        assertThat(remoteCallsInsideTransaction).hasValue(0);
    }

    @Test
    void duplicateCreateOrderReturnsExistingOrderWithoutRepeatingSideEffects() {
        IdempotentOrderRepository repository = new IdempotentOrderRepository(false);
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger clearCartCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        OrderServiceImpl orderService = orderServiceForCreate(
                repository,
                reserveCalls,
                clearCartCalls,
                publishedEvents
        );

        Order first = orderService.createOrder(createOrderRequest("checkout-1"));
        Order second = orderService.createOrder(createOrderRequest("checkout-1"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.createdOrderCount()).isEqualTo(1);
        assertThat(reserveCalls).hasValue(1);
        assertThat(publishedEvents).hasValue(1);
        assertThat(clearCartCalls).hasValue(1);
    }

    @Test
    void duplicateCreateOrderDoesNotReserveInventoryOrPublishAnotherEvent() {
        IdempotentOrderRepository repository = new IdempotentOrderRepository(false);
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger clearCartCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        OrderServiceImpl orderService = orderServiceForCreate(
                repository,
                reserveCalls,
                clearCartCalls,
                publishedEvents
        );

        orderService.createOrder(createOrderRequest("checkout-1"));
        orderService.createOrder(createOrderRequest("checkout-1"));
        orderService.createOrder(createOrderRequest("checkout-1"));

        assertThat(repository.createdOrderCount()).isEqualTo(1);
        assertThat(reserveCalls).hasValue(1);
        assertThat(publishedEvents).hasValue(1);
        assertThat(clearCartCalls).hasValue(1);
    }

    @Test
    void concurrentDuplicateCreateOrderRequestsReturnOnePersistedOrder() throws Exception {
        IdempotentOrderRepository repository = new IdempotentOrderRepository(true);
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger clearCartCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        OrderServiceImpl orderService = orderServiceForCreate(
                repository,
                reserveCalls,
                clearCartCalls,
                publishedEvents
        );
        executorService = Executors.newFixedThreadPool(2);

        Future<Order> first = executorService.submit(() -> orderService.createOrder(createOrderRequest("checkout-1")));
        Future<Order> second = executorService.submit(() -> orderService.createOrder(createOrderRequest("checkout-1")));

        Order firstResult = first.get();
        Order secondResult = second.get();

        assertThat(firstResult.getId()).isEqualTo(secondResult.getId());
        assertThat(repository.createdOrderCount()).isEqualTo(1);
        assertThat(reserveCalls).hasValue(1);
        assertThat(publishedEvents).hasValue(1);
        assertThat(clearCartCalls).hasValue(1);
        assertThat(remoteCallsInsideTransaction).hasValue(0);
    }

    @Test
    void payOrderUsesPaymentIdempotencyKeyAndPublishesOrderPaidAfterSuccessfulLocalUpdate() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        order.setUserId(501L);
        order.setTotalAmount(BigDecimal.TEN);
        AtomicReference<PaymentRequest> paymentRequest = new AtomicReference<>();
        AtomicInteger commitCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        List<OrderEvent> events = new ArrayList<>();
        OrderServiceImpl orderService = orderServiceForPay(
                order,
                false,
                paymentRequest,
                commitCalls,
                publishedEvents,
                events
        );

        Order result = orderService.payOrder(1001L, 501L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRequest.get().getIdempotencyKey()).isEqualTo("pay-1001");
        assertThat(commitCalls).hasValue(1);
        assertThat(publishedEvents).hasValue(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OrderEventType.ORDER_PAID);
        assertThat(remoteCallsInsideTransaction).hasValue(0);
    }

    @Test
    void payOrderDoesNotPublishKafkaOrCommitInventoryWhenLocalPaidUpdateConflicts() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        order.setUserId(501L);
        order.setTotalAmount(BigDecimal.TEN);
        AtomicReference<PaymentRequest> paymentRequest = new AtomicReference<>();
        AtomicInteger commitCalls = new AtomicInteger();
        AtomicInteger publishedEvents = new AtomicInteger();
        OrderServiceImpl orderService = orderServiceForPay(
                order,
                true,
                paymentRequest,
                commitCalls,
                publishedEvents,
                new ArrayList<>()
        );

        assertThatThrownBy(() -> orderService.payOrder(1001L, 501L))
                .isInstanceOf(ConflictException.class);

        assertThat(paymentRequest.get().getIdempotencyKey()).isEqualTo("pay-1001");
        assertThat(commitCalls).hasValue(0);
        assertThat(publishedEvents).hasValue(0);
    }

    @Test
    void concurrentPayAndCancelCannotBothCompleteConflictingTransitions() throws Exception {
        Order order = orderWithStatus(OrderStatus.CREATED);
        order.setUserId(501L);
        order.setTotalAmount(BigDecimal.TEN);
        CountDownLatch paymentStarted = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);
        AtomicInteger commitCalls = new AtomicInteger();
        AtomicInteger releaseCalls = new AtomicInteger();
        List<OrderEvent> events = new ArrayList<>();
        OrderServiceImpl orderService = orderServiceForPayCancelRace(
                order,
                paymentStarted,
                releasePayment,
                commitCalls,
                releaseCalls,
                events
        );
        executorService = Executors.newSingleThreadExecutor();

        Future<Order> payAttempt = executorService.submit(() -> orderService.payOrder(1001L, 501L));
        assertThat(paymentStarted.await(2, TimeUnit.SECONDS)).isTrue();

        Order cancelled = orderService.cancelOrder(1001L, 501L);
        releasePayment.countDown();

        assertThatThrownBy(payAttempt::get)
                .hasCauseInstanceOf(BadRequestException.class);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(commitCalls).hasValue(0);
        assertThat(releaseCalls).hasValue(1);
        assertThat(events).extracting(OrderEvent::getEventType)
                .containsExactly(OrderEventType.ORDER_CANCELLED);
    }

    @Test
    void optimisticConflictIsMappedToHttp409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleConflict(
                new ConflictException("Order was updated by another request"),
                new MockHttpServletRequest("POST", "/api/orders/1001/pay")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateStatusMapsOptimisticLockConflictToConflictException() {
        Order order = orderWithStatus(OrderStatus.PAID);
        List<OrderStatusHistory> history = new ArrayList<>();
        OrderServiceImpl orderService = new OrderServiceImpl(
                repositoryReturning(order, true),
                null,
                null,
                releaseTrackingInventory(new AtomicInteger()),
                null,
                null,
                null,
                null,
                historyRepository(history),
                new TrackingTransactionManager(),
                null
        );

        assertThatThrownBy(() -> orderService.updateStatus(1001L, OrderStatus.PROCESSING,
                "FULFILLMENT_PROCESSING", "FULFILLMENT_EVENT", "event-1"))
                .isInstanceOf(ConflictException.class);
    }

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
                new TrackingTransactionManager(),
                null
        );
    }

    private OrderServiceImpl orderServiceForCreate(IdempotentOrderRepository repository,
                                                   AtomicInteger reserveCalls,
                                                   AtomicInteger clearCartCalls,
                                                   AtomicInteger publishedEvents) {
        OrderItemRepository orderItemRepository = (OrderItemRepository) Proxy.newProxyInstance(
                OrderItemRepository.class.getClassLoader(),
                new Class<?>[]{OrderItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) {
                        return (OrderItem) args[0];
                    }
                    if (method.getName().equals("findByOrderId")) {
                        return List.of();
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderItemRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        OrderEventProducer orderEventProducer = new OrderEventProducer(null) {
            @Override
            public void publish(OrderEvent event) {
                publishedEvents.incrementAndGet();
            }
        };
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        IdGenerator idGenerator = new SequentialIdGenerator();

        return new OrderServiceImpl(
                repository.proxy(),
                orderItemRepository,
                createTrackingCartClient(clearCartCalls),
                reservingInventory(reserveCalls),
                null,
                null,
                orderEventProducer,
                orderMetrics,
                historyRepository(new ArrayList<>()),
                new TrackingTransactionManager(),
                idGenerator
        );
    }

    private OrderServiceImpl orderServiceForPay(Order order,
                                                boolean failStatusFlush,
                                                AtomicReference<PaymentRequest> paymentRequest,
                                                AtomicInteger commitCalls,
                                                AtomicInteger publishedEvents,
                                                List<OrderEvent> events) {
        OrderItemRepository orderItemRepository = (OrderItemRepository) Proxy.newProxyInstance(
                OrderItemRepository.class.getClassLoader(),
                new Class<?>[]{OrderItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByOrderId")) {
                        OrderItem item = new OrderItem();
                        item.setId(2001L);
                        item.setOrderId(1001L);
                        item.setProductId(10L);
                        item.setSku("SKU-10");
                        item.setQuantity(1);
                        return List.of(item);
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderItemRepository pay test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        OrderEventProducer producer = new OrderEventProducer(null) {
            @Override
            public void publish(OrderEvent event) {
                publishedEvents.incrementAndGet();
                events.add(event);
            }
        };
        return new OrderServiceImpl(
                repositoryReturning(order, failStatusFlush),
                orderItemRepository,
                null,
                committingInventory(commitCalls),
                successfulPaymentClient(paymentRequest),
                null,
                producer,
                new OrderMetrics(new SimpleMeterRegistry()),
                historyRepository(new ArrayList<>()),
                new TrackingTransactionManager(),
                null
        );
    }

    private OrderServiceImpl orderServiceForPayCancelRace(Order order,
                                                          CountDownLatch paymentStarted,
                                                          CountDownLatch releasePayment,
                                                          AtomicInteger commitCalls,
                                                          AtomicInteger releaseCalls,
                                                          List<OrderEvent> events) {
        OrderEventProducer producer = new OrderEventProducer(null) {
            @Override
            public void publish(OrderEvent event) {
                events.add(event);
            }
        };
        return new OrderServiceImpl(
                repositoryReturning(order, false),
                orderItemsForPay(),
                null,
                commitAndReleaseInventory(commitCalls, releaseCalls),
                blockingSuccessfulPaymentClient(paymentStarted, releasePayment),
                null,
                producer,
                new OrderMetrics(new SimpleMeterRegistry()),
                historyRepository(new ArrayList<>()),
                new TrackingTransactionManager(),
                null
        );
    }

    private OrderItemRepository orderItemsForPay() {
        return (OrderItemRepository) Proxy.newProxyInstance(
                OrderItemRepository.class.getClassLoader(),
                new Class<?>[]{OrderItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByOrderId")) {
                        OrderItem item = new OrderItem();
                        item.setId(2001L);
                        item.setOrderId(1001L);
                        item.setProductId(10L);
                        item.setSku("SKU-10");
                        item.setQuantity(1);
                        return List.of(item);
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderItemRepository pay test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private CartClient createTrackingCartClient(AtomicInteger clearCartCalls) {
        return (CartClient) Proxy.newProxyInstance(
                CartClient.class.getClassLoader(),
                new Class<?>[]{CartClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCart")) {
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
                        return ApiResponse.success(cart());
                    }
                    if (method.getName().equals("clearCart")) {
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
                        clearCartCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "CartClient test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private InventoryClient reservingInventory(AtomicInteger reserveCalls) {
        return (InventoryClient) Proxy.newProxyInstance(
                InventoryClient.class.getClassLoader(),
                new Class<?>[]{InventoryClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("reserveInventory")) {
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
                        reserveCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "InventoryClient reserve test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private CreateOrderRequest createOrderRequest(String idempotencyKey) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(501L);
        request.setIdempotencyKey(idempotencyKey);
        request.setSource(OrderSource.WEB);
        return request;
    }

    private CartDTO cart() {
        CartItemDTO item = new CartItemDTO();
        item.setProductId(10L);
        item.setSku("SKU-10");
        item.setProductName("Test Product");
        item.setBrand("Test Brand");
        item.setPrice(BigDecimal.TEN);
        item.setQuantity(1);
        CartDTO cart = new CartDTO();
        cart.setUserId(501L);
        cart.setItems(List.of(item));
        cart.setTotalPrice(BigDecimal.TEN);
        return cart;
    }

    private PaymentClient successfulPaymentClient(AtomicReference<PaymentRequest> paymentRequest) {
        return (PaymentClient) Proxy.newProxyInstance(
                PaymentClient.class.getClassLoader(),
                new Class<?>[]{PaymentClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("authorizePayment")) {
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
                        paymentRequest.set((PaymentRequest) args[0]);
                        PaymentResponse response = new PaymentResponse();
                        response.setPaymentId(3001L);
                        response.setOrderId(1001L);
                        response.setStatus(PaymentStatus.AUTHORIZED);
                        return response;
                    }
                    if (method.getName().equals("toString")) {
                        return "PaymentClient test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PaymentClient blockingSuccessfulPaymentClient(CountDownLatch paymentStarted, CountDownLatch releasePayment) {
        return (PaymentClient) Proxy.newProxyInstance(
                PaymentClient.class.getClassLoader(),
                new Class<?>[]{PaymentClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("authorizePayment")) {
                        paymentStarted.countDown();
                        if (!releasePayment.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("payment test timed out");
                        }
                        PaymentResponse response = new PaymentResponse();
                        response.setPaymentId(3001L);
                        response.setOrderId(1001L);
                        response.setStatus(PaymentStatus.AUTHORIZED);
                        return response;
                    }
                    if (method.getName().equals("toString")) {
                        return "Blocking PaymentClient test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private InventoryClient committingInventory(AtomicInteger commitCalls) {
        return (InventoryClient) Proxy.newProxyInstance(
                InventoryClient.class.getClassLoader(),
                new Class<?>[]{InventoryClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("commitInventory")) {
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
                        commitCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "InventoryClient commit test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private InventoryClient commitAndReleaseInventory(AtomicInteger commitCalls, AtomicInteger releaseCalls) {
        return (InventoryClient) Proxy.newProxyInstance(
                InventoryClient.class.getClassLoader(),
                new Class<?>[]{InventoryClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("commitInventory")) {
                        commitCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("releaseInventory")) {
                        releaseCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "InventoryClient pay cancel test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private OrderRepository repositoryReturning(Order order) {
        return repositoryReturning(order, false);
    }

    private OrderRepository repositoryReturning(Order order, boolean failSaveAndFlush) {
        return (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class<?>[]{OrderRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        return Optional.of(order);
                    }
                    if (method.getName().equals("findByIdAndUserId")) {
                        return Optional.of(order);
                    }
                    if (method.getName().equals("saveAndFlush")) {
                        if (failSaveAndFlush) {
                            throw new OptimisticLockingFailureException("stale order");
                        }
                        return args[0];
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
                        if (TrackingTransactionManager.isActive()) {
                            remoteCallsInsideTransaction.incrementAndGet();
                        }
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

    private static final class IdempotentOrderRepository {
        private final List<Order> orders = new ArrayList<>();
        private final boolean synchronizeFirstTwoLookups;
        private final AtomicInteger lookupCount = new AtomicInteger();
        private final CountDownLatch firstTwoLookups = new CountDownLatch(2);

        private IdempotentOrderRepository(boolean synchronizeFirstTwoLookups) {
            this.synchronizeFirstTwoLookups = synchronizeFirstTwoLookups;
        }

        private OrderRepository proxy() {
            return (OrderRepository) Proxy.newProxyInstance(
                    OrderRepository.class.getClassLoader(),
                    new Class<?>[]{OrderRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByUserIdAndIdempotencyKey")) {
                            return findByUserIdAndIdempotencyKey((Long) args[0], (String) args[1]);
                        }
                        if (method.getName().equals("findById")) {
                            return orders.stream()
                                    .filter(order -> order.getId().equals(args[0]))
                                    .findFirst();
                        }
                        if (method.getName().equals("saveAndFlush")) {
                            return saveAndFlush((Order) args[0]);
                        }
                        if (method.getName().equals("save")) {
                            return args[0];
                        }
                        if (method.getName().equals("toString")) {
                            return "IdempotentOrderRepository test proxy";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey)
                throws InterruptedException {
            if (synchronizeFirstTwoLookups && lookupCount.incrementAndGet() <= 2) {
                firstTwoLookups.countDown();
                firstTwoLookups.await();
            }
            synchronized (orders) {
                return orders.stream()
                        .filter(order -> order.getUserId().equals(userId))
                        .filter(order -> order.getIdempotencyKey().equals(idempotencyKey))
                        .min(Comparator.comparing(Order::getId));
            }
        }

        private Order saveAndFlush(Order order) {
            synchronized (orders) {
                boolean duplicate = orders.stream()
                        .anyMatch(existing -> existing.getUserId().equals(order.getUserId())
                                && existing.getIdempotencyKey().equals(order.getIdempotencyKey()));
                if (duplicate) {
                    throw new DataIntegrityViolationException("duplicate idempotency key");
                }
                orders.add(order);
                return order;
            }
        }

        private int createdOrderCount() {
            synchronized (orders) {
                return orders.size();
            }
        }
    }

    private static final class SequentialIdGenerator extends IdGenerator {
        private final AtomicLong nextId = new AtomicLong(1001L);

        @Override
        public synchronized long nextId() {
            return nextId.getAndIncrement();
        }
    }

    private static final class TrackingTransactionManager implements PlatformTransactionManager {
        private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

        private static boolean isActive() {
            return ACTIVE.get();
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            ACTIVE.set(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            ACTIVE.set(false);
        }

        @Override
        public void rollback(TransactionStatus status) {
            ACTIVE.set(false);
        }
    }
}
