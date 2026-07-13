package com.ordering.common.event;

import com.ordering.common.domain.FulfillmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentStatusUpdatedEvent {
    private String eventId;
    private Long fulfillmentId;
    private String fulfillmentNo;
    private Long orderId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String locationCode;
    private FulfillmentStatus status;
    private List<FulfillmentLineEvent> lines;
    private LocalDateTime occurredAt;
    private Integer version;
}
