package com.ordering.cartservice.controller;

import com.ordering.cartservice.service.CartService;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.CartDTO;
import com.ordering.common.dto.CartItemDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;
    private Long userId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-User-Id"));
    }

    //GET    /api/cart              → view cart
    @GetMapping
    public ApiResponse<CartDTO> getCart(HttpServletRequest request) {
        return ApiResponse.success(cartService.getCart(userId(request)));
    }

    @PostMapping("/items")
    public ApiResponse<?> addItemToCart(@RequestBody CartItemDTO item, HttpServletRequest request) {
        cartService.addItemToCart(userId(request), item);
        return ApiResponse.success(null);
    }

    @PutMapping("/items/{itemId}") ////PUT    /api/cart/items/{id}   → update quantity
    public ApiResponse<?> updateItem(@PathVariable Long itemId, @RequestParam("qty") Integer quantity, HttpServletRequest request) {
        cartService.updateItem(userId(request), itemId, quantity);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/items/{itemId}") //DELETE /api/cart/items/{id}   → remove item
    public ApiResponse<?> removeItem(@PathVariable Long itemId, HttpServletRequest request) {
        cartService.removeItem(userId(request), itemId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/clear") //DELETE /api/cart/clear        → clear cart
    public ApiResponse<?> clearCart(HttpServletRequest request) {
        cartService.clearCart(userId(request));
        return ApiResponse.success(null);
    }


}
