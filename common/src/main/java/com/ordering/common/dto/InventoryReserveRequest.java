package com.ordering.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class InventoryReserveRequest {
     private Long orderId;
     private List<InventoryReserveItem> items;
}
