package com.ordering.promotionservice.service;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.dto.PromotionRequest;
import com.ordering.common.dto.PromotionResponse;
import com.ordering.promotionservice.entity.Promotion;
import com.ordering.promotionservice.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {
    private final PromotionRepository promotionRepository;
    @Override
    public PromotionResponse validatePromotion(String code, OrderSource source) {
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Promotion not found"));

        boolean valid = isCurrentlyValid(promotion, source);

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
        promotion.setAllowedSources(resolveAllowedSources(request.getAllowedSources()));

        Promotion saved = promotionRepository.save(promotion);
        return toResponse(saved, isCurrentlyValid(saved, OrderSource.WEB));
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
        promotion.setAllowedSources(resolveAllowedSources(request.getAllowedSources()));

        Promotion saved = promotionRepository.save(promotion);

        return toResponse(saved, isCurrentlyValid(saved, OrderSource.WEB));
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
        response.setAllowedSources(promotion.getAllowedSources());
        return response;
    }
    private boolean isCurrentlyValid(Promotion promotion, OrderSource source) {
        LocalDateTime now = LocalDateTime.now();

        return promotion.isActive()
                && now.isAfter(promotion.getStartTime())
                && now.isBefore(promotion.getEndTime())
                && promotion.getAllowedSources() != null
                && promotion.getAllowedSources().contains(source);
    }

    private Set<OrderSource> resolveAllowedSources(Set<OrderSource> allowedSources) {
        if (allowedSources == null || allowedSources.isEmpty()) {
            return new HashSet<>(EnumSet.allOf(OrderSource.class));
        }
        return new HashSet<>(allowedSources);
    }
}
