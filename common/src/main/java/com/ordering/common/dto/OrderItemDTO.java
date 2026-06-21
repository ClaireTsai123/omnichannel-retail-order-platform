package com.ordering.common.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDTO {

    private Long productId;
    private String sku;
    private String brand;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
}
