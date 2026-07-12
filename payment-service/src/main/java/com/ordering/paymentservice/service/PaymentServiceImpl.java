package com.ordering.paymentservice.service;

import com.ordering.common.domain.PaymentStatus;
import com.ordering.common.dto.PaymentRequest;
import com.ordering.common.dto.PaymentResponse;
import com.ordering.common.event.PaymentEvent;
import com.ordering.common.event.PaymentEventType;
import com.ordering.paymentservice.entity.Payment;
import com.ordering.paymentservice.metrics.PaymentMetrics;
import com.ordering.paymentservice.producer.PaymentEventProducer;
import com.ordering.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentMetrics paymentMetrics;


    @Override
    @Transactional
    public PaymentResponse authorizePayment(PaymentRequest request) {
        Payment existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getOrderId().equals(request.getOrderId())) {
                throw new IllegalArgumentException(
                        "Idempotency key is already associated with another order"
                );
            }
            return toResponse(existingPayment);
        }

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        // simulate payment authorization success
        payment.setStatus(PaymentStatus.AUTHORIZED);
        Payment saved = paymentRepository.save(payment);

        paymentMetrics.recordPaymentAuthorization();

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(PaymentEventType.PAYMENT_AUTHORIZED);
        event.setPaymentId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setUserId(saved.getUserId());
        event.setAmount(saved.getAmount());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);

        paymentEventProducer.publish(event);
        return toResponse(saved);
    }

    @Override
    public PaymentResponse authorizePaymentFailed(PaymentRequest request) {
        Payment existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getOrderId().equals(request.getOrderId())) {
                throw new IllegalArgumentException(
                        "Idempotency key is already associated with another order"
                );
            }
            return toResponse(existingPayment);
        }

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        // simulate payment authorization success
        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(PaymentEventType.PAYMENT_FAILED);
        event.setPaymentId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setUserId(saved.getUserId());
        event.setAmount(saved.getAmount());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);

        paymentEventProducer.publish(event);
        paymentMetrics.recordPaymentFailed();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new RuntimeException("Only authorized payments can be refunded");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(PaymentEventType.PAYMENT_REFUNDED);
        event.setPaymentId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setUserId(saved.getUserId());
        event.setAmount(saved.getAmount());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);

        paymentEventProducer.publish(event);

        return toResponse(saved);
    }

    @Override
    public PaymentResponse failPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.FAILED) {
           return toResponse(payment);
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new RuntimeException("Refunded payments cannot be failed");
        }
        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(PaymentEventType.PAYMENT_FAILED);
        event.setPaymentId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setUserId(saved.getUserId());
        event.setAmount(saved.getAmount());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);

        paymentEventProducer.publish(event);

        return toResponse(saved);
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getOrderId());
        response.setStatus(payment.getStatus());
        return response;
    }
}
