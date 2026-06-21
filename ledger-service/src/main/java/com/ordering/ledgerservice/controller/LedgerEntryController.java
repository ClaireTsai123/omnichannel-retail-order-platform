package com.ordering.ledgerservice.controller;

import com.ordering.common.dto.LedgerEntryRequest;
import com.ordering.common.dto.LedgerEntryResponse;
import com.ordering.ledgerservice.service.LedgerEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerEntryController {
    private final LedgerEntryService ledgerEntryService;

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/orders/{orderId}")
    public List<LedgerEntryResponse> getEntriesByOrderId(@PathVariable Long orderId) {
        return ledgerEntryService.getEntriesByOrderId(orderId);
    }
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/payments/{paymentId}")
    public List<LedgerEntryResponse> getEntriesByPaymentId(@PathVariable Long paymentId) {
        return ledgerEntryService.getEntriesByPaymentId(paymentId);
    }

}
