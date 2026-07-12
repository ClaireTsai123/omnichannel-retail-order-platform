package com.ordering.orderservice.entity;

import com.ordering.common.domain.FulfillmentStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_fulfillment_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_fulfillment_status_fulfillment_id",
                columnNames = "fulfillment_id"
        )
)
@Data
public class OrderFulfillmentStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_id", nullable = false)
    private Long fulfillmentId;

    @Column(name = "fulfillment_no")
    private String fulfillmentNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus status;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
