package com.ordering.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter paymentAuthorizedCounter;
    private final Counter paymentFailedCounter;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentAuthorizedCounter = Counter.builder("payment_authorized")
                .description("Total number of successfully authorized payments")
                .register(meterRegistry);
        this.paymentFailedCounter = Counter.builder("payment_failed")
                .description("Total number of failed payments")
                .register(meterRegistry);
    }

    public void recordPaymentAuthorization() {
        record(paymentAuthorizedCounter);
    }

    public void recordPaymentFailed() {
        record(paymentFailedCounter);
    }

    private void record(Counter counter) {
        counter.increment();
    }
}
