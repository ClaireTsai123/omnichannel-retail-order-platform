package com.ordering.common.dto;

import com.ordering.common.domain.OrderSource;
import lombok.Data;

import java.util.Set;

@Data
public class PromotionResponse {
    private String code;
    private Integer discountPercentage;
    private boolean valid;
    private Set<OrderSource> allowedSources;
}
