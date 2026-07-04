package com.ordering.common.dto;

import com.ordering.common.domain.OrderSource;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class PromotionRequest {
    private String code;

    private Integer discountPercentage;

    private boolean active;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Set<OrderSource> allowedSources;
}
