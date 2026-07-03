package com.ordering.inventoryservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private final Counter inventoryReservedCounter;
    private final Counter inventoryBusinessReleaseCounter;
    private final Counter inventoryExpiredReleaseCounter;

    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.inventoryReservedCounter = Counter.builder("inventory_reserved")
                .description("Total number of successful inventory reservations")
                .register(meterRegistry);
        this.inventoryBusinessReleaseCounter = Counter.builder("inventory_business_release")
                .description("Total number of inventory releases triggered by business workflow")
                .register(meterRegistry);
        this.inventoryExpiredReleaseCounter = Counter.builder("inventory_expired_release")
                .description("Total number of inventory releases triggered by reservation expiration")
                .register(meterRegistry);
    }

    public void recordInventoryReservation() {
        record(inventoryReservedCounter);
    }

    public void recordInventoryBusinessRelease() {
        record(inventoryBusinessReleaseCounter);
    }

    public void recordInventoryExpiredRelease() {
        record(inventoryExpiredReleaseCounter);
    }

    private void record(Counter counter) {
        counter.increment();
    }
}
