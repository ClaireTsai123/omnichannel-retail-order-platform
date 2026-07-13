package com.ordering.fulfillmentservice.entity;

import com.ordering.common.domain.FulfillmentStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "fulfillments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fulfillment_order_no",
                columnNames = {"order_id", "fulfillment_no"}
        )
)
@Data
public class Fulfillment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_no", nullable = false)
    private String fulfillmentNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String nodeName;

    @Column(nullable = false)
    private String nodeType;

    @Column(nullable = false)
    private String locationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
