package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.domain.OrderStatus;
import com.ordering.common.domain.PaymentStatus;
import com.ordering.common.dto.*;
import com.ordering.common.event.OrderEvent;
import com.ordering.common.event.OrderEventType;
import com.ordering.common.exception.BadRequestException;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    // for sharding
    private final IdGenerator idGenerator;


    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Long userId = request.getUserId();

        Long orderId = idGenerator.nextId();

        ApiResponse<CartDTO> response = cartClient.getCart(userId.toString());
        CartDTO cart = response.getData();
        if (cart == null || cart.getItems().isEmpty()) {
            throw new ResourceNotFoundException("Cart is empty");
        }

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
        Order order = new Order();
        order.setId(orderId);// assigned first
        System.out.println("Creating order with ID = " + orderId);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setSource(orderSource);
        order.setStatus(OrderStatus.CREATED);
        Order savedOrder = orderRepository.save(order);
        recordStatusHistory(savedOrder.getId(), null, OrderStatus.CREATED,
                "ORDER_CREATED", "ORDER_SERVICE", null);
        //2, save order item
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
            orderItemRepository.save(orderItem);
        }

        System.out.println("Saving order item, orderId = " + orderId);
        OrderEvent event = new OrderEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(OrderEventType.ORDER_CREATED);
        event.setOrderId(savedOrder.getId());
        event.setUserId(savedOrder.getUserId());
        event.setTotalAmount(savedOrder.getTotalAmount());
        event.setSource(savedOrder.getSource());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);

        orderEventProducer.publish(event);

        //3. clear cart
        cartClient.clearCart(userId.toString());
        orderMetrics.recordOrderCreation();

        return savedOrder;
    }

    @Override
    @Transactional
    public Order payOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BadRequestException("Order cannot be paid");
        }
        // authorize payment through payment-service
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setUserId(userId);
        paymentRequest.setAmount(order.getTotalAmount());
        paymentRequest.setPaymentMethod("CREDIT_CARD");
        paymentRequest.setIdempotencyKey("pay-" + orderId);
        PaymentResponse paymentResponse = paymentClient.authorizePayment(paymentRequest);
        if (paymentResponse.getStatus() != PaymentStatus.AUTHORIZED) {
            return handlePaymentFailed(orderId, null, "PAYMENT_SYNC");
        }
        transitionOrderStatus(order, OrderStatus.PAID, "PAYMENT_AUTHORIZED", "PAYMENT_SYNC", null);
        Order saved = orderRepository.save(order);

        inventoryClient.commitInventory(orderId);

        OrderEvent orderEvent = new OrderEvent();
        orderEvent.setEventId(UUID.randomUUID().toString());
        orderEvent.setEventType(OrderEventType.ORDER_PAID);
        orderEvent.setOrderId(saved.getId());
        orderEvent.setUserId(saved.getUserId());
        orderEvent.setTotalAmount(saved.getTotalAmount());
        orderEvent.setSource(saved.getSource());
        orderEvent.setOccurredAt(LocalDateTime.now());
        orderEvent.setVersion(1);
        orderEventProducer.publish(orderEvent);

        return saved;
    }

    @Override
    @Transactional
    public Order handlePaymentFailed(Long orderId) {
        return handlePaymentFailed(orderId, null, "ORDER_SERVICE");
    }

    @Override
    @Transactional
    public Order handlePaymentFailed(Long orderId, String eventId, String source) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED
                || order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            System.out.println("Ignoring payment failure for orderId="
                    + orderId
                    + ", status="
                    + order.getStatus());
            return order;
        }
        inventoryClient.releaseInventory(orderId);
        transitionOrderStatus(order, OrderStatus.PAYMENT_FAILED, "PAYMENT_FAILED", source, eventId);
        return order;
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
    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BadRequestException("Only CREATED orders can be cancelled");
        }
        inventoryClient.releaseInventory(orderId);
        transitionOrderStatus(order, OrderStatus.CANCELLED, "ORDER_CANCELLED", "ORDER_SERVICE", null);

        Order saved = orderRepository.save(order);
        OrderEvent event = new OrderEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(OrderEventType.ORDER_CANCELLED);
        event.setOrderId(saved.getId());
        event.setUserId(saved.getUserId());
        event.setTotalAmount(saved.getTotalAmount());
        event.setSource(saved.getSource());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);
        orderEventProducer.publish(event);

        return saved;
    }

    @Override
    @Transactional
    public void updateStatus(Long orderId, OrderStatus status) {
        updateStatus(orderId, status, "STATUS_UPDATED", "ORDER_SERVICE", null);
    }

    @Override
    @Transactional
    public void updateStatus(Long orderId, OrderStatus status, String reason, String source, String eventId) {
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
}
