package com.ordering.promotionservice.service;

import com.ordering.common.dto.PromotionRequest;
import com.ordering.common.dto.PromotionResponse;

public interface PromotionService {
    PromotionResponse validatePromotion(String code);
    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(Long id, PromotionRequest request);

    void deletePromotion(Long id);
}
