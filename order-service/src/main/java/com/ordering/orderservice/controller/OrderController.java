package com.ordering.orderservice.controller;

import com.ordering.common.domain.OrderStatus;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.CreateOrderRequest;
import com.ordering.common.dto.OrderDTO;
import com.ordering.orderservice.entity.Order;
import com.ordering.orderservice.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    private Long userId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-User-Id"));
    }


    //@PreAuthorize("hasRole('CUSTOMER')")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @PostMapping
    public ApiResponse<Order> createOrder(HttpServletRequest request,
                                          @RequestBody CreateOrderRequest createOrderRequest) {
        createOrderRequest.setUserId(userId(request));
        return ApiResponse.success(orderService.createOrder(createOrderRequest));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<Page<OrderDTO>> getAllOrders(@RequestParam(required = false) OrderStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        if (status == null) {
            return ApiResponse.success(orderService.getAllOrders(pageable));
        }
        return ApiResponse.success(orderService.getOrdersByStatus(status, pageable));
    }


    //@PreAuthorize("hasRole('CUSTOMER')")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @PostMapping("/{orderId}/pay")
    public ApiResponse<Order> payOrder(@PathVariable Long orderId,
                                       HttpServletRequest request
    ) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        return ApiResponse.success(orderService.payOrder(orderId, userId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDTO> getOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        return ApiResponse.success(orderService.getOrderById(orderId, userId));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public ApiResponse<Page<OrderDTO>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        Pageable pageable = PageRequest.of(page, size);

        return ApiResponse.success(orderService.getMyOrders(userId, pageable));
    }


    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDTO> cancelOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        Order cancelled = orderService.cancelOrder(orderId, userId);
        OrderDTO dto = orderService.getOrderById(cancelled.getId(), userId);
        return ApiResponse.success(dto);
    }
}
