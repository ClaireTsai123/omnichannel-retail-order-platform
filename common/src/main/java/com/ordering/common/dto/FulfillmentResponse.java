package com.ordering.common.dto;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FulfillmentResponse {
    private Long id;
    private Long orderId;
    private Long userId;
    private FulfillmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
