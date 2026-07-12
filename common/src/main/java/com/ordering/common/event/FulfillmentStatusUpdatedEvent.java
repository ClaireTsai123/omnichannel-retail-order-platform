package com.ordering.common.event;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentStatusUpdatedEvent {
    private String eventId;
    private Long orderId;
    private FulfillmentStatus status;
    private LocalDateTime occurredAt;
    private Integer version;
}
