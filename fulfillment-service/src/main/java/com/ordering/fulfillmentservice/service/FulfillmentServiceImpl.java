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

@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements FulfillmentService {

    private final FulfillmentRepository fulfillmentRepository;
    private final FulfillmentEventProducer fulfillmentEventProducer;
    private final FulfillmentMetrics fulfillmentMetrics;

    @Override
    @Transactional
    public void createFulfillment(Long orderId, Long userId) {
        if (fulfillmentRepository.existsByOrderId(orderId)) {
            return;
        }
        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setOrderId(orderId);
        fulfillment.setUserId(userId);
        fulfillment.setStatus(FulfillmentStatus.CREATED);
        fulfillmentRepository.save(fulfillment);
        fulfillmentMetrics.recordFulfillmentCreation();

    }

    @Override
    public void cancelFulfillment(Long orderId) {
        fulfillmentRepository.findByOrderId(orderId).ifPresent(fulfillment -> {
            fulfillment.setStatus(FulfillmentStatus.CANCELLED);
            fulfillmentRepository.save(fulfillment);
        });
    }

    @Override
    public FulfillmentResponse getByOrderId(Long orderId) {
        Fulfillment fulfillment = fulfillmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Fulfillment not found for orderId: " + orderId));

        return toResponse(fulfillment);
    }

    @Override
    @Transactional
    public FulfillmentResponse updateStatus(Long orderId, FulfillmentStatus fulfillmentStatus) {
        Fulfillment fulfillment = fulfillmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Fulfillment not found for orderId: " + orderId));

        fulfillment.setStatus(fulfillmentStatus);
        Fulfillment saved = fulfillmentRepository.save(fulfillment);
        FulfillmentStatusUpdatedEvent event = new FulfillmentStatusUpdatedEvent();
        event.setOrderId(saved.getOrderId());
        event.setStatus(saved.getStatus());
        fulfillmentEventProducer.publishStatusUpdatedEvent(event);

        return toResponse(saved);
    }

    private FulfillmentResponse toResponse(Fulfillment fulfillment) {
        FulfillmentResponse response = new FulfillmentResponse();
        response.setId(fulfillment.getId());
        response.setOrderId(fulfillment.getOrderId());
        response.setUserId(fulfillment.getUserId());
        response.setStatus(fulfillment.getStatus());
        response.setCreatedAt(fulfillment.getCreatedAt());
        response.setUpdatedAt(fulfillment.getUpdatedAt());
        return response;
    }
}
