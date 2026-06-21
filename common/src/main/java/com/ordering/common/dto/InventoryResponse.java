package com.ordering.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryResponse {
    private Long id;
    private String sku;
    private Integer availableQuantity;
    private Integer reserveQuantity;
    private LocalDateTime updatedAt;
}
