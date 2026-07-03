package com.ordering.fulfillmentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentMetrics {

    private final Counter fulfillmentCreatedCounter;

    public FulfillmentMetrics(MeterRegistry meterRegistry) {
        this.fulfillmentCreatedCounter = Counter.builder("fulfillment_creation")
                .description("Total number of successfully created fulfillments")
                .register(meterRegistry);
    }

    public void recordFulfillmentCreation() {
        record(fulfillmentCreatedCounter);
    }

    private void record(Counter counter) {
        counter.increment();
    }
}
