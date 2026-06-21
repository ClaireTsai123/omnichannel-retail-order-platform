package com.ordering.fulfillmentservice.controller;

import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.dto.UpdateFulfillmentStatusRequest;
import com.ordering.fulfillmentservice.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {
    private final FulfillmentService fulfillmentService;

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/orders/{orderId}")
    public FulfillmentResponse getByOrderId(@PathVariable Long orderId) {
        return fulfillmentService.getByOrderId(orderId);
    }
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN','CUSTOMER')")
    @PatchMapping ("/orders/{orderId}/status")
    public FulfillmentResponse updateStatus(@PathVariable Long orderId,
                                            @RequestBody UpdateFulfillmentStatusRequest request) {
        return fulfillmentService.updateStatus(orderId, request.getStatus());
    }

}
