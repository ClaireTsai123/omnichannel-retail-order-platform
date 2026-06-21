package com.ordering.orderservice.client;

import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.CartDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service")
public interface CartClient {
    @GetMapping("/api/cart")
//X-User-Id is a String in Feign because HTTP headers are text;
        // you convert it to Long only after receiving the request inside the service.
    ApiResponse<CartDTO> getCart(@RequestHeader("X-User-Id") String userId);

    @DeleteMapping("/api/cart/clear")
    void clearCart(@RequestHeader("X-User-Id") String userId);
}
