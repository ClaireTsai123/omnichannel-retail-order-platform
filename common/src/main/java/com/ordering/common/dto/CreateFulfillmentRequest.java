package com.ordering.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateFulfillmentRequest {
    private Long userId;
    private String fulfillmentNo;
    private List<FulfillmentLineRequest> lines;
}
