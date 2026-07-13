package com.ordering.fulfillmentservice.controller;

import com.ordering.common.dto.CreateFulfillmentRequest;
import com.ordering.common.dto.FulfillmentResponse;
import com.ordering.common.dto.UpdateFulfillmentStatusRequest;
import com.ordering.common.dto.UpdateFulfillmentLineStatusRequest;
import com.ordering.fulfillmentservice.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {
    private final FulfillmentService fulfillmentService;

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/orders/{orderId}")
    public List<FulfillmentResponse> getByOrderId(@PathVariable Long orderId) {
        return fulfillmentService.getByOrderId(orderId);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/{fulfillmentId}")
    public FulfillmentResponse getById(@PathVariable Long fulfillmentId) {
        return fulfillmentService.getById(fulfillmentId);
    }

    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    @PostMapping("/orders/{orderId}")
    public FulfillmentResponse createFulfillment(@PathVariable Long orderId,
                                                 @RequestBody CreateFulfillmentRequest request) {
        return fulfillmentService.createFulfillment(orderId, request.getUserId(), request.getFulfillmentNo(),
                request.getNodeId(), request.getNodeName(), request.getNodeType(), request.getLocationCode(),
                request.getLines());
    }

    @PreAuthorize("hasAnyRole('VENDOR','ADMIN','CUSTOMER')")
    @PatchMapping ("/{fulfillmentId}/status")
    public FulfillmentResponse updateStatus(@PathVariable Long fulfillmentId,
                                            @RequestBody UpdateFulfillmentStatusRequest request) {
        return fulfillmentService.updateStatus(fulfillmentId, request.getStatus());
    }

    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    @PatchMapping ("/{fulfillmentId}/lines/{lineId}/status")
    public FulfillmentResponse updateLineStatus(@PathVariable Long fulfillmentId,
                                                @PathVariable Long lineId,
                                                @RequestBody UpdateFulfillmentLineStatusRequest request) {
        return fulfillmentService.updateLineStatus(fulfillmentId, lineId, request.getStatus());
    }

}
