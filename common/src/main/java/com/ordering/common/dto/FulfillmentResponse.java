package com.ordering.common.dto;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FulfillmentResponse {
    private Long id;
    private String fulfillmentNo;
    private Long orderId;
    private Long userId;
    private FulfillmentStatus status;
    private List<FulfillmentLineResponse> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
