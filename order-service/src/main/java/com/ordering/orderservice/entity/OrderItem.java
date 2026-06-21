package com.ordering.orderservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "order_items")
public class OrderItem {
    @Id
    private Long id;

    private Long orderId;
    private Long productId;
    private String sku;
    private String productName;
    private String brand;
    private BigDecimal unitPrice;
    private Integer quantity;

}
