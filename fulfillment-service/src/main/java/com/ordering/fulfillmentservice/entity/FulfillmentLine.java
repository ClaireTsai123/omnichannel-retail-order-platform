package com.ordering.fulfillmentservice.entity;

import com.ordering.common.domain.FulfillmentStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "fulfillment_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fulfillment_line_order_item",
                columnNames = {"fulfillment_id", "order_item_id"}
        )
)
@Data
public class FulfillmentLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_id", nullable = false)
    private Long fulfillmentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    private Long productId;

    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer orderedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus status;
}
