package com.ordering.common.dto;

import com.ordering.common.domain.LedgerTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LedgerEntryResponse {
    private Long id;
    private String transactionId;
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private LedgerTransactionType transactionType;
    private LocalDateTime createdAt;
}
