package com.ordering.cartservice.service;

import com.ordering.cartservice.client.ProductClient;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.CartDTO;
import com.ordering.common.dto.CartItemDTO;
import com.ordering.common.dto.ProductDTO;
import com.ordering.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private  final ProductClient productClient;
    private static final String CART_KEY_PREFIX = "cart:";
    private static final long CART_TTL_MINUTES = 300;// for test

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    @Override
    public CartDTO getCart(Long userId) {
        String key = cartKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        List<CartItemDTO> items = entries.values().stream().
                map(v -> (CartItemDTO) v).toList();
        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // set/ refresh TTL
        redisTemplate.expire(key, CART_TTL_MINUTES, TimeUnit.MINUTES);

        return new CartDTO(userId, items, total);
    }

    @Override
    public void addItemToCart(Long userId, CartItemDTO item) {
        if (item.getProductId() == null) {
            throw  new IllegalArgumentException("productId is required");
        }
        // fetch menu item from catalog-service
        ApiResponse<ProductDTO> response = productClient.getProduct(item.getProductId());
        ProductDTO product  = response.getData();
        if (product == null || !product.getActive()) {
            throw new ResourceNotFoundException("Product item not available");
        }
        item.setProductName(product.getProductName());
        item.setSku(product.getSku());
        item.setBrand(product.getBrand());
        item.setPrice(product.getPrice());

        String key = cartKey(userId);
        String itemKey = item.getProductId().toString();
        CartItemDTO existingItem = (CartItemDTO) redisTemplate.opsForHash().get(key, itemKey);
        if(existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
            redisTemplate.opsForHash().put(key, itemKey, existingItem);
        } else {
            redisTemplate.opsForHash().put(key, itemKey, item);
        }
        // set/ refresh TTL
        redisTemplate.expire(key, CART_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void updateItem(Long userId, Long itemId, Integer quantity) {
        String key = cartKey(userId);
        CartItemDTO item = (CartItemDTO) redisTemplate.opsForHash()
                .get(key, itemId.toString());
        if (item == null) {
            throw new ResourceNotFoundException("Item not found in cart");
        }
        item.setQuantity(quantity);
        redisTemplate.opsForHash().put(key, itemId.toString(), item);
        // set/ refresh TTL
        redisTemplate.expire(key, CART_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void removeItem(Long userId, Long itemId) {
        redisTemplate.opsForHash().delete(cartKey(userId), itemId.toString());
    }

    @Override
    public void clearCart(Long userId) {
        redisTemplate.delete(cartKey(userId));
    }
}
