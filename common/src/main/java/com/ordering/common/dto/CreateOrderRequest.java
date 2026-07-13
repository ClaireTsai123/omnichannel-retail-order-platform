package com.ordering.common.dto;

import com.ordering.common.domain.OrderSource;
import lombok.Data;

@Data
public class CreateOrderRequest {
    private Long userId;
    private String idempotencyKey;
    private String promotionCode;
    private OrderSource source;
}
