package com.ordering.cartservice.service;

import com.ordering.common.dto.CartItemDTO;
import com.ordering.common.dto.CartDTO;

public interface CartService {
    void addItemToCart(Long userId, CartItemDTO item);
    void updateItem(Long userId, Long itemId, Integer quantity);
    void removeItem(Long userId, Long itemId);
    CartDTO getCart(Long userId);
    void clearCart(Long userId);
}
