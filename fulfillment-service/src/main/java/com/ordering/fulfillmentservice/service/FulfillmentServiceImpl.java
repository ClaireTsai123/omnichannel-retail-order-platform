package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.fulfillmentservice.entity.Fulfillment;
import com.ordering.fulfillmentservice.metrics.FulfillmentMetrics;
import com.ordering.fulfillmentservice.producer.FulfillmentEventProducer;
import com.ordering.fulfillmentservice.repository.FulfillmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements FulfillmentService {

    private final FulfillmentRepository fulfillmentRepository;
    private final FulfillmentEventProducer fulfillmentEventProducer;
    private final FulfillmentMetrics fulfillmentMetrics;

    @Override
    @Transactional
    public FulfillmentResponse createFulfillment(Long orderId, Long userId) {
        return createFulfillment(orderId, userId, defaultFulfillmentNo(orderId));
    }

    @Override
    @Transactional
    public FulfillmentResponse createFulfillment(Long orderId, Long userId, String fulfillmentNo) {
        String normalizedFulfillmentNo = normalizeFulfillmentNo(orderId, fulfillmentNo);
        return fulfillmentRepository.findByOrderIdAndFulfillmentNo(orderId, normalizedFulfillmentNo)
                .map(this::toResponse)
                .orElseGet(() -> createNewFulfillment(orderId, userId, normalizedFulfillmentNo));
    }

    private FulfillmentResponse createNewFulfillment(Long orderId, Long userId, String fulfillmentNo) {
        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setFulfillmentNo(fulfillmentNo);
        fulfillment.setOrderId(orderId);
        fulfillment.setUserId(userId);
        fulfillment.setStatus(FulfillmentStatus.CREATED);
        Fulfillment saved = fulfillmentRepository.save(fulfillment);
        fulfillmentMetrics.recordFulfillmentCreation();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void cancelFulfillment(Long orderId) {
        fulfillmentRepository.findByOrderIdOrderByIdAsc(orderId)
                .forEach(fulfillment -> {
                    fulfillment.setStatus(FulfillmentStatus.CANCELLED);
                    Fulfillment saved = fulfillmentRepository.save(fulfillment);
                    publishStatusUpdatedEvent(saved);
                });
    }

    @Override
    public List<FulfillmentResponse> getByOrderId(Long orderId) {
        List<FulfillmentResponse> fulfillments = fulfillmentRepository.findByOrderIdOrderByIdAsc(orderId)
                .stream()
                .map(this::toResponse)
                .toList();
        if (fulfillments.isEmpty()) {
            throw new RuntimeException("Fulfillment not found for orderId: " + orderId);
        }

        return fulfillments;
    }

    @Override
    public FulfillmentResponse getById(Long fulfillmentId) {
        Fulfillment fulfillment = fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new RuntimeException("Fulfillment not found: " + fulfillmentId));
        return toResponse(fulfillment);
    }

    @Override
    @Transactional
    public FulfillmentResponse updateStatus(Long fulfillmentId, FulfillmentStatus fulfillmentStatus) {
        Fulfillment fulfillment = fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new RuntimeException("Fulfillment not found: " + fulfillmentId));

        fulfillment.setStatus(fulfillmentStatus);
        Fulfillment saved = fulfillmentRepository.save(fulfillment);
        publishStatusUpdatedEvent(saved);

        return toResponse(saved);
    }

    private void publishStatusUpdatedEvent(Fulfillment saved) {
        FulfillmentStatusUpdatedEvent event = new FulfillmentStatusUpdatedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setFulfillmentId(saved.getId());
        event.setFulfillmentNo(saved.getFulfillmentNo());
        event.setOrderId(saved.getOrderId());
        event.setStatus(saved.getStatus());
        event.setOccurredAt(LocalDateTime.now());
        event.setVersion(1);
        fulfillmentEventProducer.publishStatusUpdatedEvent(event);
    }

    private FulfillmentResponse toResponse(Fulfillment fulfillment) {
        FulfillmentResponse response = new FulfillmentResponse();
        response.setId(fulfillment.getId());
        response.setFulfillmentNo(fulfillment.getFulfillmentNo());
        response.setOrderId(fulfillment.getOrderId());
        response.setUserId(fulfillment.getUserId());
        response.setStatus(fulfillment.getStatus());
        response.setCreatedAt(fulfillment.getCreatedAt());
        response.setUpdatedAt(fulfillment.getUpdatedAt());
        return response;
    }

    private String normalizeFulfillmentNo(Long orderId, String fulfillmentNo) {
        if (fulfillmentNo == null || fulfillmentNo.isBlank()) {
            return defaultFulfillmentNo(orderId);
        }
        return fulfillmentNo.trim();
    }

    private String defaultFulfillmentNo(Long orderId) {
        return "F-" + orderId + "-1";
    }
}
