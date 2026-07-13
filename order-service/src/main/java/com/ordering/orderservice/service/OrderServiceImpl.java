package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.domain.PaymentStatus;
import com.ordering.common.dto.*;
import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.common.exception.BadRequestException;
import com.ordering.common.exception.ConflictException;
import com.ordering.common.exception.ResourceNotFoundException;
import com.ordering.orderservice.client.CartClient;
import com.ordering.orderservice.client.InventoryClient;
import com.ordering.orderservice.client.PaymentClient;
import com.ordering.orderservice.client.PromotionClient;
import com.ordering.orderservice.entity.Order;
import com.ordering.orderservice.entity.OrderStatusHistory;
import com.ordering.orderservice.metrics.OrderMetrics;
import com.ordering.orderservice.producer.OrderEventProducer;
import com.ordering.orderservice.repository.OrderRepository;
import com.ordering.orderservice.util.IdGenerator;
import com.ordering.orderservice.entity.OrderItem;
import com.ordering.orderservice.repository.OrderItemRepository;
import com.ordering.orderservice.repository.OrderStatusHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartClient cartClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final PromotionClient promotionClient;
    private final OrderEventProducer orderEventProducer;
    private final OrderMetrics orderMetrics;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PlatformTransactionManager transactionManager;

    // for sharding
    private final IdGenerator idGenerator;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Order createOrder(CreateOrderRequest request) {
        Long userId = request.getUserId();
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            Order existingOrder = inTransaction(() -> findExistingIdempotentOrder(userId, idempotencyKey));
            if (existingOrder != null) {
                return existingOrder;
            }
        }

        Long orderId = idGenerator.nextId();
        ApiResponse<CartDTO> response = cartClient.getCart(userId.toString());
        CartDTO cart = response.getData();
        if (cart == null || cart.getItems().isEmpty()) {
            throw new ResourceNotFoundException("Cart is empty");
        }

        //1, create order
        BigDecimal totalAmount = cart.getTotalPrice();
        OrderSource orderSource = request.getSource() == null ? OrderSource.WEB : request.getSource();
        if (request.getPromotionCode() != null && !request.getPromotionCode().isBlank()) {
            PromotionResponse promotion =
                    promotionClient.validatePromotion(request.getPromotionCode(), orderSource);
            if (promotion.isValid()) {
                BigDecimal discount = BigDecimal.valueOf(100 - promotion.getDiscountPercentage())
                        .divide(BigDecimal.valueOf(100));
                totalAmount = totalAmount.multiply(discount);
            }
        }

        OrderCreatedResult created = createNewOrder(request, idempotencyKey, orderId, cart, totalAmount, orderSource);
        if (!created.newlyCreated()) {
            return created.order();
        }

        try {
            InventoryReserveRequest reserveRequest = new InventoryReserveRequest();
            reserveRequest.setOrderId(orderId);
            List<InventoryReserveItem> reserveItems = cart.getItems().stream()
                    .map(item -> {
                        InventoryReserveItem reserveItem = new InventoryReserveItem();
                        reserveItem.setSku(item.getSku());
                        reserveItem.setQuantity(item.getQuantity());
                        return reserveItem;
                    }).toList();
            reserveRequest.setItems(reserveItems);
            inventoryClient.reserveInventory(reserveRequest);
        } catch (RuntimeException ex) {
            markOrderCancelledAfterCreateFailure(orderId);
            throw ex;
        }

        orderEventProducer.publish(created.toOrderEvent());
        cartClient.clearCart(userId.toString());
        orderMetrics.recordOrderCreation();

        return created.order();
    }

    private OrderCreatedResult createNewOrder(CreateOrderRequest request,
                                              String idempotencyKey,
                                              Long orderId,
                                              CartDTO cart,
                                              BigDecimal totalAmount,
                                              OrderSource orderSource) {
        Long userId = request.getUserId();
        try {
            return inTransaction(() -> persistNewOrder(userId, idempotencyKey, orderId, cart, totalAmount, orderSource));
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            return inTransaction(() -> {
                if (entityManager != null) {
                    entityManager.clear();
                }
                Order existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .orElseThrow(() -> ex);
                return new OrderCreatedResult(existing, List.of(), false);
            });
        }
    }

    private OrderCreatedResult persistNewOrder(Long userId,
                                               String idempotencyKey,
                                               Long orderId,
                                               CartDTO cart,
                                               BigDecimal totalAmount,
                                               OrderSource orderSource) {
        Order order = new Order();
        order.setId(orderId);// assigned first
        System.out.println("Creating order with ID = " + orderId);
        order.setUserId(userId);
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalAmount(totalAmount);
        order.setSource(orderSource);
        order.setStatus(OrderStatus.CREATED);
        Order savedOrder = orderRepository.saveAndFlush(order);

        recordStatusHistory(savedOrder.getId(), null, OrderStatus.CREATED,
                "ORDER_CREATED", "ORDER_SERVICE", null);
        //2, save order item
        List<OrderItem> savedItems = new ArrayList<>();
        for (CartItemDTO item : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setId(idGenerator.nextId()); // assign id
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setSku(item.getSku());
            orderItem.setBrand(item.getBrand());
            orderItem.setProductName(item.getProductName());
            orderItem.setUnitPrice(item.getPrice());
            orderItem.setQuantity(item.getQuantity());
            savedItems.add(orderItemRepository.save(orderItem));
        }

        System.out.println("Saving order item, orderId = " + orderId);
        return new OrderCreatedResult(savedOrder, savedItems, true);
    }

    private Order findExistingIdempotentOrder(Long userId, String idempotencyKey) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    @Override
    public Order payOrder(Long orderId, Long userId) {
        Order paymentCandidate = inTransaction(() -> getPayableOrder(orderId, userId));
        // authorize payment through payment-service
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setUserId(userId);
        paymentRequest.setAmount(paymentCandidate.getTotalAmount());
        paymentRequest.setPaymentMethod("CREDIT_CARD");
        paymentRequest.setIdempotencyKey("pay-" + orderId);
        PaymentResponse paymentResponse = paymentClient.authorizePayment(paymentRequest);
        if (paymentResponse.getStatus() != PaymentStatus.AUTHORIZED) {
            return handlePaymentFailed(orderId, null, "PAYMENT_SYNC");
        }

        OrderPaidResult paid = inTransactionHandlingOptimisticConflict(() -> markOrderPaid(orderId, userId));
        inventoryClient.commitInventory(orderId);
        orderEventProducer.publish(paid.toOrderEvent());

        return paid.order();
    }

    @Override
    public Order handlePaymentFailed(Long orderId) {
        return handlePaymentFailed(orderId, null, "ORDER_SERVICE");
    }

    @Override
    public Order handlePaymentFailed(Long orderId, String eventId, String source) {
        PaymentFailureResult result = inTransactionHandlingOptimisticConflict(
                () -> markPaymentFailed(orderId, eventId, source)
        );
        if (result.inventoryReleaseRequired()) {
            inventoryClient.releaseInventory(orderId);
        }
        return result.order();
    }

    @Override
    public OrderDTO getOrderById(Long orderId, Long userId) {
        Order order = orderRepository
                .findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        List<OrderItemDTO> items = orderItemRepository.findByOrderId(order.getId())
                .stream().map(this::apply).toList();
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setSource(order.getSource());
        dto.setStatus(order.getStatus());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setItems(items);
        return dto;
    }

    @Override
    public Page<OrderDTO> getMyOrders(Long userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(this::convertToDto);
    }

    @Override
    public Order cancelOrder(Long orderId, Long userId) {
        OrderCancelledResult cancelled = inTransactionHandlingOptimisticConflict(
                () -> markOrderCancelled(orderId, userId)
        );
        inventoryClient.releaseInventory(orderId);
        orderEventProducer.publish(cancelled.toOrderEvent());

        return cancelled.order();
    }

    @Override
    public void updateStatus(Long orderId, OrderStatus status) {
        updateStatus(orderId, status, "STATUS_UPDATED", "ORDER_SERVICE", null);
    }

    @Override
    public void updateStatus(Long orderId, OrderStatus status, String reason, String source, String eventId) {
        inTransactionHandlingOptimisticConflict(() -> {
            updateStatusInTransaction(orderId, status, reason, source, eventId);
            return null;
        });
    }

    private void updateStatusInTransaction(Long orderId, OrderStatus status, String reason, String source, String eventId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() == status) {
            return;
        }
        if (!isValidStatusTransition(order.getStatus(), status)) {
            System.out.println("Ignoring invalid order status transition, orderId="
                    + orderId
                    + ", from="
                    + order.getStatus()
                    + ", to="
                    + status);
            return;
        }
        transitionOrderStatus(order, status, reason, source, eventId);
        orderRepository.saveAndFlush(order);
    }

    private Order getPayableOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BadRequestException("Order cannot be paid");
        }
        return order;
    }

    private OrderPaidResult markOrderPaid(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BadRequestException("Order cannot be paid");
        }
        transitionOrderStatus(order, OrderStatus.PAID, "PAYMENT_AUTHORIZED", "PAYMENT_SYNC", null);
        Order saved = orderRepository.saveAndFlush(order);
        List<OrderItem> items = orderItemRepository.findByOrderId(saved.getId());
        return new OrderPaidResult(saved, items);
    }

    private OrderCancelledResult markOrderCancelled(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BadRequestException("Only CREATED orders can be cancelled");
        }
        transitionOrderStatus(order, OrderStatus.CANCELLED, "ORDER_CANCELLED", "ORDER_SERVICE", null);
        return new OrderCancelledResult(orderRepository.saveAndFlush(order));
    }

    private void markOrderCancelledAfterCreateFailure(Long orderId) {
        inTransactionHandlingOptimisticConflict(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
            if (order.getStatus() == OrderStatus.CREATED) {
                transitionOrderStatus(order, OrderStatus.CANCELLED,
                        "INVENTORY_RESERVATION_FAILED", "ORDER_SERVICE", null);
                orderRepository.saveAndFlush(order);
            }
            return null;
        });
    }

    private PaymentFailureResult markPaymentFailed(Long orderId, String eventId, String source) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED
                || order.getStatus() == OrderStatus.CANCELLED) {
            return new PaymentFailureResult(order, false);
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            System.out.println("Ignoring payment failure for orderId="
                    + orderId
                    + ", status="
                    + order.getStatus());
            return new PaymentFailureResult(order, false);
        }
        transitionOrderStatus(order, OrderStatus.PAYMENT_FAILED, "PAYMENT_FAILED", source, eventId);
        return new PaymentFailureResult(orderRepository.saveAndFlush(order), true);
    }

    @Override
    public Page<OrderDTO> getAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::convertToDto);
    }

    @Override
    public Page<OrderDTO> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::convertToDto);
    }

    private OrderDTO convertToDto(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setSource(order.getSource());
        dto.setStatus(order.getStatus());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setItems(orderItemRepository.findByOrderId(order.getId())
                .stream()
                .map(this::apply).toList()
        );
        return dto;
    }

    private OrderItemDTO apply(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProductId());
        dto.setSku(item.getSku());
        dto.setBrand(item.getBrand());
        dto.setProductName(item.getProductName());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setQuantity(item.getQuantity());
        return dto;
    }

    private void transitionOrderStatus(Order order,
                                       OrderStatus nextStatus,
                                       String reason,
                                       String source,
                                       String eventId) {
        OrderStatus previousStatus = order.getStatus();
        if (previousStatus == nextStatus) {
            return;
        }
        order.setStatus(nextStatus);
        recordStatusHistory(order.getId(), previousStatus, nextStatus, reason, source, eventId);
    }

    private void recordStatusHistory(Long orderId,
                                     OrderStatus previousStatus,
                                     OrderStatus newStatus,
                                     String reason,
                                     String source,
                                     String eventId) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);
        history.setSource(source);
        history.setEventId(eventId);
        orderStatusHistoryRepository.save(history);
    }

    private boolean isValidStatusTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        return switch (currentStatus) {
            case CREATED -> nextStatus == OrderStatus.PAID
                    || nextStatus == OrderStatus.PAYMENT_FAILED
                    || nextStatus == OrderStatus.CANCELLED;
            case PAID -> nextStatus == OrderStatus.PROCESSING
                    || nextStatus == OrderStatus.SHIPPED
                    || nextStatus == OrderStatus.DELIVERED
                    || nextStatus == OrderStatus.CANCELLED;
            case PROCESSING -> nextStatus == OrderStatus.SHIPPED
                    || nextStatus == OrderStatus.DELIVERED
                    || nextStatus == OrderStatus.CANCELLED;
            case SHIPPED -> nextStatus == OrderStatus.DELIVERED;
            case DELIVERED, PAYMENT_FAILED, CANCELLED -> false;
        };
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        if (transactionManager == null) {
            return supplier.get();
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> supplier.get());
    }

    private <T> T inTransactionHandlingOptimisticConflict(Supplier<T> supplier) {
        try {
            return inTransaction(supplier);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("Order was updated by another request. Please reload and try again.");
        }
    }

    private record OrderCreatedResult(Order order, List<OrderItem> items, boolean newlyCreated) {
        private OrderEvent toOrderEvent() {
            OrderEvent event = new OrderEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType(OrderEventType.ORDER_CREATED);
            event.setOrderId(order.getId());
            event.setUserId(order.getUserId());
            event.setTotalAmount(order.getTotalAmount());
            event.setItems(items.stream().map(OrderCreatedResult::toOrderItemDTO).toList());
            event.setSource(order.getSource());
            event.setOccurredAt(LocalDateTime.now());
            event.setVersion(1);
            return event;
        }

        private static OrderItemDTO toOrderItemDTO(OrderItem item) {
            OrderItemDTO dto = new OrderItemDTO();
            dto.setId(item.getId());
            dto.setProductId(item.getProductId());
            dto.setSku(item.getSku());
            dto.setBrand(item.getBrand());
            dto.setProductName(item.getProductName());
            dto.setUnitPrice(item.getUnitPrice());
            dto.setQuantity(item.getQuantity());
            return dto;
        }
    }

    private record OrderPaidResult(Order order, List<OrderItem> items) {
        private OrderEvent toOrderEvent() {
            OrderEvent event = new OrderEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType(OrderEventType.ORDER_PAID);
            event.setOrderId(order.getId());
            event.setUserId(order.getUserId());
            event.setTotalAmount(order.getTotalAmount());
            event.setItems(items.stream().map(OrderCreatedResult::toOrderItemDTO).toList());
            event.setSource(order.getSource());
            event.setOccurredAt(LocalDateTime.now());
            event.setVersion(1);
            return event;
        }
    }

    private record OrderCancelledResult(Order order) {
        private OrderEvent toOrderEvent() {
            OrderEvent event = new OrderEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType(OrderEventType.ORDER_CANCELLED);
            event.setOrderId(order.getId());
            event.setUserId(order.getUserId());
            event.setTotalAmount(order.getTotalAmount());
            event.setSource(order.getSource());
            event.setOccurredAt(LocalDateTime.now());
            event.setVersion(1);
            return event;
        }
    }

    private record PaymentFailureResult(Order order, boolean inventoryReleaseRequired) {
    }
}
