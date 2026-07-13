package com.ordering.common.dto;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.Data;

@Data
public class FulfillmentLineResponse {
    private Long id;
    private Long orderItemId;
    private Long productId;
    private String sku;
    private Integer quantity;
    private Integer orderedQuantity;
    private FulfillmentStatus status;
}
