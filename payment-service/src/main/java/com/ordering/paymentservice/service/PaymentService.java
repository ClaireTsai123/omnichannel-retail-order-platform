package com.ordering.paymentservice.service;


import com.ordering.common.dto.PaymentRequest;
import com.ordering.common.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse authorizePayment(PaymentRequest request);
    PaymentResponse authorizePaymentFailed(PaymentRequest request);
    PaymentResponse refundPayment(Long paymentId);

    PaymentResponse failPayment(Long paymentId);
}
