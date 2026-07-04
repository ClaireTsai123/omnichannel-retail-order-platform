package com.ordering.common.event;

import com.ordering.common.domain.OrderSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String eventId;
    private OrderEventType eventType;
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private OrderSource source;
    private LocalDateTime occurredAt;
}
