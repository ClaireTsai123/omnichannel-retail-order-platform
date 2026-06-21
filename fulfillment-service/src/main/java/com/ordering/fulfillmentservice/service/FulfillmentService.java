package com.ordering.fulfillmentservice.service;

import com.ordering.common.domain.FulfillmentStatus;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.dto.UpdateFulfillmentStatusRequest;

public interface FulfillmentService {

    void createFulfillment(Long orderId, Long userId);

    void cancelFulfillment(Long orderId);
    FulfillmentResponse getByOrderId(Long orderId);


    FulfillmentResponse updateStatus(Long orderId, FulfillmentStatus status);
}
