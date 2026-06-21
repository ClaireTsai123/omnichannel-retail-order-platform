package com.ordering.ledgerservice.service;

import com.ordering.common.dto.LedgerEntryRequest;
import com.ordering.common.dto.LedgerEntryResponse;

import java.util.List;

public interface LedgerEntryService {
    LedgerEntryResponse createEntry(LedgerEntryRequest request);

    List<LedgerEntryResponse> getEntriesByOrderId(Long orderId);

    List<LedgerEntryResponse> getEntriesByPaymentId(Long paymentId);

}
