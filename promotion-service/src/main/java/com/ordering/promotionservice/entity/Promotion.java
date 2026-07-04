package com.ordering.promotionservice.entity;

import com.ordering.common.domain.OrderSource;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "promotions")
@Data
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private Integer discountPercentage;
    private boolean active;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_allowed_sources", joinColumns = @JoinColumn(name = "promotion_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private Set<OrderSource> allowedSources;

}
