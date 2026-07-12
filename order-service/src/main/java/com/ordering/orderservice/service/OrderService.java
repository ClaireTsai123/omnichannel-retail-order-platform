package com.ordering.orderservice.service;

import com.ordering.common.domain.OrderStatus;
import com.ordering.common.dto.CreateOrderRequest;
import com.ordering.orderservice.entity.Order;
import com.ordering.common.dto.OrderDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    Order createOrder(CreateOrderRequest request);

    Order payOrder(Long orderId, Long userId);

    OrderDTO getOrderById(Long orderId, Long userId);
    Page<OrderDTO> getMyOrders(Long userId, Pageable pageable);

    Order cancelOrder(Long orderId, Long userId);

    Order handlePaymentFailed(Long orderId);

    Order handlePaymentFailed(Long orderId, String eventId, String source);

    void updateStatus(Long orderId, OrderStatus status);

    void updateStatus(Long orderId, OrderStatus status, String reason, String source, String eventId);

    Page<OrderDTO> getAllOrders(Pageable pageable);
    Page<OrderDTO> getOrdersByStatus(OrderStatus status, Pageable pageable);


}
