package com.ordering.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Data
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String sku;
    @Column(nullable = false)
    private Integer availableQuantity;
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    // optimistic locking, avoid overselling
    @Version
    private Long version;
    @UpdateTimestamp
    private LocalDateTime updateAt;

}
