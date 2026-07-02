package com.ordering.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_reservation_order_sku",
                columnNames = {"order_id", "sku"}
        )
)
@Data
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private Integer quantity;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @CreationTimestamp
    private LocalDateTime createAt;

}
