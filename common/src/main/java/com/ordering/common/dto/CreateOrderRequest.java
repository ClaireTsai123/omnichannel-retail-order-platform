package com.ordering.common.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private Long userId;
    private String promotionCode;
}
