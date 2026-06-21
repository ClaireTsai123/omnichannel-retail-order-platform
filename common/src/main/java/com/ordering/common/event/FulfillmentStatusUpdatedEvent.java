package com.ordering.common.event;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentStatusUpdatedEvent {
    private Long orderId;
    private FulfillmentStatus status;
}
