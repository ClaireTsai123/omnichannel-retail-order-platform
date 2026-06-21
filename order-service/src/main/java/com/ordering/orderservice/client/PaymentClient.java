package com.ordering.orderservice.client;


import com.ordering.common.dto.PaymentRequest;
import com.ordering.common.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentClient {
    @PostMapping("/api/payments/authorize")
    PaymentResponse authorizePayment(@RequestBody PaymentRequest request);
}
