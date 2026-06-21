package com.ordering.ledgerservice.service;

import com.ordering.common.dto.LedgerEntryRequest;
import com.ordering.common.dto.LedgerEntryResponse;
import com.ordering.ledgerservice.entity.LedgerEntry;
import com.ordering.ledgerservice.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerEntryServiceImpl implements LedgerEntryService {
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public LedgerEntryResponse createEntry(LedgerEntryRequest request) {
        if (ledgerEntryRepository.existsByTransactionId(request.getTransactionId())) {
            throw new IllegalArgumentException("Ledger entry already exists for transactionId: " + request.getTransactionId());
        }
        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionId(request.getTransactionId());
        entry.setPaymentId(request.getPaymentId());
        entry.setOrderId(request.getOrderId());
        entry.setUserId(request.getUserId());
        entry.setAmount(request.getAmount());
        entry.setTransactionType(request.getTransactionType());
        LedgerEntry saved = ledgerEntryRepository.save(entry);

        return toResponse(saved);
    }

    @Override
    public List<LedgerEntryResponse> getEntriesByOrderId(Long orderId) {
        return ledgerEntryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<LedgerEntryResponse> getEntriesByPaymentId(Long paymentId) {
        return ledgerEntryRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId)
                .stream()
                .map(this:: toResponse)
                .toList();
    }

    private LedgerEntryResponse toResponse(LedgerEntry saved) {
        LedgerEntryResponse response = new LedgerEntryResponse();
        response.setId(saved.getId());
        response.setTransactionId(saved.getTransactionId());
        response.setPaymentId(saved.getPaymentId());
        response.setOrderId(saved.getOrderId());
        response.setUserId(saved.getUserId());
        response.setAmount(saved.getAmount());
        response.setTransactionType(saved.getTransactionType());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }
}
