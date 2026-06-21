package com.ordering.common.dto;

import com.ordering.common.domain.LedgerTransactionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerEntryRequest {
    private String transactionId;
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private LedgerTransactionType transactionType;
}
