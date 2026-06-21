package com.ordering.inventoryservice.service;

import com.ordering.common.dto.InventoryReserveRequest;
import com.ordering.common.dto.InventoryResponse;

public interface InventoryService {
    void reserveInventory(InventoryReserveRequest request);
    void commitInventory(Long orderId);
    void releaseInventory(Long orderId);

    InventoryResponse getInventoryBySku(String sku);
}
