package com.ordering.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateFulfillmentRequest {
    private Long userId;
    private String fulfillmentNo;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String locationCode;
    private List<FulfillmentLineRequest> lines;
}
