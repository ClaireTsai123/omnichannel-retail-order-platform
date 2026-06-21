package com.ordering.inventoryservice.util;

import com.ordering.inventoryservice.entity.Inventory;
import com.ordering.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryDataInitializer implements CommandLineRunner {

    private  final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) throws Exception {
        if (inventoryRepository.count() > 0) {
            return;
        }

        List<String> skus = List.of(
                "SKC-001", "SKC-002", "SKC-003",
                "MKP-001", "MKP-002", "MKP-003",
                "HRC-001", "HRC-002",
                "FRG-001", "FRG-002", "FRG-003",
                "TLB-001", "TLB-002", "TLB-003"
        );

        List<Inventory> inventories = skus.stream()
                .map(this::createInventory)
                .toList();

        inventoryRepository.saveAll(inventories);

        System.out.println("✅ Inventory initialized: " + inventories.size() + " SKUs");

    }
    private Inventory createInventory(String sku) {
        Inventory inventory = new Inventory();
        inventory.setSku(sku);
        inventory.setAvailableQuantity(1000);
        inventory.setReservedQuantity(0);
        return inventory;
    }
}
