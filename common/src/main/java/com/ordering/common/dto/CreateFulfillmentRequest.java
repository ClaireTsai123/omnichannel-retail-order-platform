package com.ordering.common.dto;

import lombok.Data;

@Data
public class CreateFulfillmentRequest {
    private Long userId;
    private String fulfillmentNo;
}
