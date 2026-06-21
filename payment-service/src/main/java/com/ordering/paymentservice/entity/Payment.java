package com.ordering.paymentservice.entity;

import com.ordering.common.domain.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String paymentMethod;
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
