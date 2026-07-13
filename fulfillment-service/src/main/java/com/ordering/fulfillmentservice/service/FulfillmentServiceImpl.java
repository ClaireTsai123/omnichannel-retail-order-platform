package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentLineRequest;
import com.ordering.common.dto.FulfillmentLineResponse;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.event.FulfillmentLineEvent;
import com.ordering.common.event.FulfillmentStatusUpdatedEvent;
import com.ordering.fulfillmentservice.entity.Fulfillment;
import com.ordering.fulfillmentservice.entity.FulfillmentLine;
import com.ordering.fulfillmentservice.metrics.FulfillmentMetrics;
import com.ordering.fulfillmentservice.producer.FulfillmentEventProducer;
import com.ordering.fulfillmentservice.repository.FulfillmentLineRepository;
import com.ordering.fulfillmentservice.repository.FulfillmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements FulfillmentService {

    private final FulfillmentRepository fulfillmentRepository;
    private final FulfillmentLineRepository fulfillmentLineRepository;
    private final FulfillmentEventProducer fulfillmentEventProducer;
    private final FulfillmentMetrics fulfillmentMetrics;

    @Override
    @Transactional
    public FulfillmentResponse createFulfillment(Long orderId, Long userId) {
        return createFulfillment(orderId, userId, defaultFulfillmentNo(orderId), Collections.emptyList());
    }

    @Override
    @Transactional
    public FulfillmentResponse createFulfillment(Long orderId, Long userId, String fulfillmentNo) {
        return createFulfillment(orderId, userId, fulfillmentNo, Collections.emptyList());
    }

    @Override
    @Transactional
    public FulfillmentResponse createFulfillment(Long orderId,
                                                 Long userId,
                                                 String fulfillmentNo,
                                                 List<FulfillmentLineRequest> lines) {
        String normalizedFulfillmentNo = normalizeFulfillmentNo(orderId, fulfillmentNo);
        return fulfillmentRepository.findByOrderIdAndFulfillmentNo(orderId, normalizedFulfillmentNo)
                .map(this::toResponse)
                .orElseGet(() -> createNewFulfillment(orderId, userId, normalizedFulfillmentNo, lines));
    }

    private FulfillmentResponse createNewFulfillment(Long orderId,
                                                     Long userId,
                                                     String fulfillmentNo,
                                                     List<FulfillmentLineRequest> lines) {
        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setFulfillmentNo(fulfillmentNo);
        fulfillment.setOrderId(orderId);
        fulfillment.setUserId(userId);
        fulfillment.setStatus(FulfillmentStatus.CREATED);
        Fulfillment saved = fulfillmentRepository.save(fulfillment);
        createLines(saved, lines);
        fulfillmentMetrics.recordFulfillmentCreation();
        return toResponse(saved);
    }

    private void createLines(Fulfillment fulfillment, List<FulfillmentLineRequest> lineRequests) {
        if (lineRequests == null) {
            return;
        }
        lineRequests.forEach(lineRequest -> {
            validateLine(lineRequest);
            FulfillmentLine line = new FulfillmentLine();
            line.setFulfillmentId(fulfillment.getId());
            line.setOrderId(fulfillment.getOrderId());
            line.setOrderItemId(lineRequest.getOrderItemId());
            line.setProductId(lineRequest.getProductId());
            line.setSku(lineRequest.getSku());
            line.setQuantity(lineRequest.getQuantity());
            line.setStatus(FulfillmentStatus.CREATED);
            fulfillmentLineRepository.save(line);
        });
    }

    @Override
    @Transactional
    public void cancelFulfillment(Long orderId) {
        fulfillmentRepository.findByOrderIdOrderByIdAsc(orderId)
                .forEach(fulfillment -> {
                    fulfillment.setStatus(FulfillmentStatus.CANCELLED);
                    Fulfillment saved = fulfillmentRepository.save(fulfillment);
                    fulfillmentLineRepository.findByFulfillmentIdOrderByIdAsc(saved.getId())
                            .forEach(line -> {
                                line.setStatus(FulfillmentStatus.CANCELLED);
                                fulfillmentLineRepository.save(line);
                            });
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

        fulfillmentLineRepository.findByFulfillmentIdOrderByIdAsc(fulfillmentId)
                .forEach(line -> {
                    line.setStatus(fulfillmentStatus);
                    fulfillmentLineRepository.save(line);
                });
        fulfillment.setStatus(fulfillmentStatus);
        Fulfillment saved = fulfillmentRepository.save(fulfillment);
        publishStatusUpdatedEvent(saved);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public FulfillmentResponse updateLineStatus(Long fulfillmentId, Long lineId, FulfillmentStatus status) {
        Fulfillment fulfillment = fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new RuntimeException("Fulfillment not found: " + fulfillmentId));
        FulfillmentLine line = fulfillmentLineRepository.findByIdAndFulfillmentId(lineId, fulfillmentId)
                .orElseThrow(() -> new RuntimeException("Fulfillment line not found: " + lineId));

        if (isLateOrRegressive(line.getStatus(), status)) {
            return toResponse(fulfillment);
        }

        if (line.getStatus() == status) {
            return toResponse(fulfillment);
        }
        line.setStatus(status);
        fulfillmentLineRepository.save(line);

        fulfillment.setStatus(deriveFulfillmentStatus(fulfillmentId));
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
        event.setLines(fulfillmentLineRepository.findByFulfillmentIdOrderByIdAsc(saved.getId())
                .stream()
                .map(this::toLineEvent)
                .toList());
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
        response.setLines(fulfillmentLineRepository.findByFulfillmentIdOrderByIdAsc(fulfillment.getId())
                .stream()
                .map(this::toLineResponse)
                .toList());
        response.setCreatedAt(fulfillment.getCreatedAt());
        response.setUpdatedAt(fulfillment.getUpdatedAt());
        return response;
    }

    private FulfillmentStatus deriveFulfillmentStatus(Long fulfillmentId) {
        List<FulfillmentStatus> statuses = fulfillmentLineRepository.findByFulfillmentIdOrderByIdAsc(fulfillmentId)
                .stream()
                .map(FulfillmentLine::getStatus)
                .toList();
        if (statuses.isEmpty()) {
            return FulfillmentStatus.CREATED;
        }
        if (statuses.stream().allMatch(status -> status == FulfillmentStatus.CANCELLED)) {
            return FulfillmentStatus.CANCELLED;
        }
        if (statuses.stream().allMatch(status -> status == FulfillmentStatus.DELIVERED
                || status == FulfillmentStatus.CANCELLED)) {
            return FulfillmentStatus.DELIVERED;
        }
        if (statuses.stream().anyMatch(status -> status == FulfillmentStatus.SHIPPED
                || status == FulfillmentStatus.DELIVERED)) {
            return FulfillmentStatus.SHIPPED;
        }
        if (statuses.stream().anyMatch(status -> status == FulfillmentStatus.PROCESSING)) {
            return FulfillmentStatus.PROCESSING;
        }
        return FulfillmentStatus.CREATED;
    }

    private boolean isLateOrRegressive(FulfillmentStatus currentStatus, FulfillmentStatus incomingStatus) {
        if (currentStatus == FulfillmentStatus.CANCELLED || currentStatus == FulfillmentStatus.DELIVERED) {
            return currentStatus != incomingStatus;
        }
        return fulfillmentRank(incomingStatus) < fulfillmentRank(currentStatus);
    }

    private int fulfillmentRank(FulfillmentStatus status) {
        return switch (status) {
            case CREATED -> 1;
            case PROCESSING -> 2;
            case SHIPPED -> 3;
            case DELIVERED, CANCELLED -> 4;
        };
    }

    private void validateLine(FulfillmentLineRequest lineRequest) {
        if (lineRequest.getOrderItemId() == null) {
            throw new IllegalArgumentException("orderItemId is required for fulfillment lines");
        }
        if (lineRequest.getQuantity() == null || lineRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    private FulfillmentLineResponse toLineResponse(FulfillmentLine line) {
        FulfillmentLineResponse response = new FulfillmentLineResponse();
        response.setId(line.getId());
        response.setOrderItemId(line.getOrderItemId());
        response.setProductId(line.getProductId());
        response.setSku(line.getSku());
        response.setQuantity(line.getQuantity());
        response.setStatus(line.getStatus());
        return response;
    }

    private FulfillmentLineEvent toLineEvent(FulfillmentLine line) {
        FulfillmentLineEvent event = new FulfillmentLineEvent();
        event.setLineId(line.getId());
        event.setOrderItemId(line.getOrderItemId());
        event.setProductId(line.getProductId());
        event.setSku(line.getSku());
        event.setQuantity(line.getQuantity());
        event.setStatus(line.getStatus());
        return event;
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
