package com.ordering.common.dto;

import lombok.Data;

@Data
public class PromotionResponse {
    private String code;
    private Integer discountPercentage;
    private boolean valid;
}
