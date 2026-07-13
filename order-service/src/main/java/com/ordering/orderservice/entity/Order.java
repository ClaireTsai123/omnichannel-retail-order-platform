package com.ordering.orderservice.entity;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.domain.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_user_id_idempotency_key",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@Data
public class Order {
    @Id
    private Long id;

    @Version
    private Long version;

    private Long userId;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING)
    private OrderSource source;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
