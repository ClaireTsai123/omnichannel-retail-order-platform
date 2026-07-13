package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentLineRequest;
import com.ordering.common.dto.FulfillmentResponse;

import java.util.List;

public interface FulfillmentService {

    FulfillmentResponse createFulfillment(Long orderId, Long userId);

    FulfillmentResponse createFulfillment(Long orderId, Long userId, String fulfillmentNo);

    FulfillmentResponse createFulfillment(Long orderId,
                                          Long userId,
                                          String fulfillmentNo,
                                          List<FulfillmentLineRequest> lines);

    FulfillmentResponse createFulfillment(Long orderId,
                                          Long userId,
                                          String fulfillmentNo,
                                          String nodeId,
                                          String nodeName,
                                          String nodeType,
                                          String locationCode,
                                          List<FulfillmentLineRequest> lines);

    void cancelFulfillment(Long orderId);

    List<FulfillmentResponse> getByOrderId(Long orderId);

    FulfillmentResponse getById(Long fulfillmentId);


    FulfillmentResponse updateStatus(Long fulfillmentId, FulfillmentStatus status);

    FulfillmentResponse updateLineStatus(Long fulfillmentId, Long lineId, FulfillmentStatus status);
}
