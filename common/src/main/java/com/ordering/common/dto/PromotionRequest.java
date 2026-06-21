package com.ordering.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionRequest {
    private String code;

    private Integer discountPercentage;

    private boolean active;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
