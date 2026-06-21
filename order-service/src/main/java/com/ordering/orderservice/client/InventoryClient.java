package com.ordering.orderservice.client;

import com.ordering.common.dto.InventoryReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/inventory/reserve")
    void reserveInventory(@RequestBody InventoryReserveRequest request);

    @PostMapping("/api/inventory/commit/{orderId}")
    void commitInventory(@PathVariable("orderId") Long orderId);

    @PostMapping("/api/inventory/release/{orderId}")
    void releaseInventory(@PathVariable("orderId") Long orderId);
}
