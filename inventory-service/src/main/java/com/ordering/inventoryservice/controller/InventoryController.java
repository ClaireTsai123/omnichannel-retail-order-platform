package com.ordering.inventoryservice.controller;

import com.ordering.common.dto.InventoryReserveRequest;
import com.ordering.common.dto.InventoryResponse;
import com.ordering.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/{sku}")
    public InventoryResponse getInventoryBySku(@PathVariable String sku) {
        return inventoryService.getInventoryBySku(sku);
    }
    @PostMapping("/reserve")
    public void reserveInventory(@RequestBody InventoryReserveRequest request) {
        inventoryService.reserveInventory(request);
    }
    @PostMapping("/commit/{orderId}")
    public void commitInventory(@PathVariable Long orderId) {
        inventoryService.commitInventory(orderId);
    }
    @PostMapping("/release/{orderId}")
    public void releaseInventory(@PathVariable Long orderId) {
        inventoryService.releaseInventory(orderId);
    }
}
