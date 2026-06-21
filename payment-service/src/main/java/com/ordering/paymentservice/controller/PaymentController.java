package com.ordering.paymentservice.controller;


import com.ordering.common.dto.PaymentRequest;
import com.ordering.common.dto.PaymentResponse;
import com.ordering.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/authorize")
    public PaymentResponse authorizePayment(@RequestBody PaymentRequest request) {
        return paymentService.authorizePayment(request);
    }
    //--for Saga testing only
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/authorize/fail")
    public PaymentResponse authorizePaymentFailed(@RequestBody PaymentRequest request) {
        return paymentService.authorizePaymentFailed(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{paymentId}/refund")
    public PaymentResponse refundPayment(@PathVariable Long paymentId) {
        return paymentService.refundPayment(paymentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{paymentId}/fail")
    public PaymentResponse failPayment(@PathVariable Long paymentId) {
        return paymentService.failPayment(paymentId);
    }
}
