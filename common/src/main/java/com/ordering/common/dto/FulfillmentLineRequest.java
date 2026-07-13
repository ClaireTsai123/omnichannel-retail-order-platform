package com.ordering.common.dto;

import lombok.Data;

@Data
public class FulfillmentLineRequest {
    private Long orderItemId;
    private Long productId;
    private String sku;
    private Integer quantity;
    private Integer orderedQuantity;
}
