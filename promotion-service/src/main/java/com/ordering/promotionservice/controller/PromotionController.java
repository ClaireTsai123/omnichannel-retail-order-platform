package com.ordering.promotionservice.controller;

import com.ordering.common.dto.PromotionRequest;
import com.ordering.common.dto.PromotionResponse;
import com.ordering.promotionservice.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping("/{code}")
    public PromotionResponse getPromotion( @PathVariable  String code) {
        return promotionService.validatePromotion(code);
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public PromotionResponse createPromotion(@RequestBody PromotionRequest request) {
        return promotionService.createPromotion(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public PromotionResponse updatePromotion(
            @PathVariable Long id,
            @RequestBody PromotionRequest request) {
        return promotionService.updatePromotion(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public void deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
    }


}
