package com.ordering.common.event;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.Data;

@Data
public class FulfillmentLineEvent {
    private Long lineId;
    private Long orderItemId;
    private Long productId;
    private String sku;
    private Integer quantity;
    private FulfillmentStatus status;
}
