package com.ordering.promotionservice.service;

import com.ordering.common.dto.PromotionRequest;
import com.ordering.common.dto.PromotionResponse;
import com.ordering.promotionservice.entity.Promotion;
import com.ordering.promotionservice.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {
    private final PromotionRepository promotionRepository;
    @Override
    public PromotionResponse validatePromotion(String code) {
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Promotion not found"));

        boolean valid = promotion.isActive()
                && LocalDateTime.now().isAfter(promotion.getStartTime())
                && LocalDateTime.now().isBefore(promotion.getEndTime());

        return toResponse(promotion, valid);
    }

    @Override
    public PromotionResponse createPromotion(PromotionRequest request) {
        Promotion promotion  = new Promotion();
        promotion.setCode(request.getCode());
        promotion.setDiscountPercentage(request.getDiscountPercentage());
        promotion.setActive(request.isActive());
        promotion.setStartTime(request.getStartTime());
        promotion.setEndTime(request.getEndTime());

        Promotion saved = promotionRepository.save(promotion);
        return toResponse(saved, isCurrentlyValid(saved));
    }

    @Override
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promotion not found"));

        promotion.setCode(request.getCode());
        promotion.setDiscountPercentage(request.getDiscountPercentage());
        promotion.setActive(request.isActive());
        promotion.setStartTime(request.getStartTime());
        promotion.setEndTime(request.getEndTime());

        Promotion saved = promotionRepository.save(promotion);

        return toResponse(saved, isCurrentlyValid(saved));
    }

    @Override
    public void deletePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promotion not found"));

        promotionRepository.delete(promotion);
    }

    private PromotionResponse toResponse(Promotion promotion,boolean valid) {
        PromotionResponse response = new PromotionResponse();
        response.setCode(promotion.getCode());
        response.setDiscountPercentage(valid ? promotion.getDiscountPercentage() : 0);
        response.setValid(valid);
        return response;
    }
    private boolean isCurrentlyValid(Promotion promotion) {
        LocalDateTime now = LocalDateTime.now();

        return promotion.isActive()
                && now.isAfter(promotion.getStartTime())
                && now.isBefore(promotion.getEndTime());
    }
}
