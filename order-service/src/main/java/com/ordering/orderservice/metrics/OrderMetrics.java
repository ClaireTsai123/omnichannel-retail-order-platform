package com.ordering.orderservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter orderCreatedCounter;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.orderCreatedCounter = Counter.builder("order_creation")
                .description("Total number of successfully created orders")
                .register(meterRegistry);
    }

    public void recordOrderCreation() {
        record(orderCreatedCounter);
    }

    private void record(Counter counter) {
        counter.increment();
    }
}
