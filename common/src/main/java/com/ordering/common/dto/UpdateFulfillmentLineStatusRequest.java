package com.ordering.common.dto;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.Data;

@Data
public class UpdateFulfillmentLineStatusRequest {
    private FulfillmentStatus status;
}
