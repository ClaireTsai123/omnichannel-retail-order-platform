package com.ordering.common.dto;

import com.ordering.common.domain.PaymentStatus;
import lombok.Data;

@Data
public class PaymentResponse {
    private Long paymentId;
    private Long orderId;
    private PaymentStatus status;

}
