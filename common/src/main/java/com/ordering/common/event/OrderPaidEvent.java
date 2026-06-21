package com.ordering.common.event;

import lombok.Data;

import java.awt.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderPaidEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
}
