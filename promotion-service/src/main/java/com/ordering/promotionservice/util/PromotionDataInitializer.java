package com.ordering.promotionservice.util;

import com.ordering.promotionservice.entity.Promotion;
import com.ordering.promotionservice.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PromotionDataInitializer implements CommandLineRunner {
    private final PromotionRepository promotionRepository;

    @Override
    public void run(String... args) throws Exception {
        if (promotionRepository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        List<Promotion> promotions = List.of(

                createPromotion(
                        "SUMMER10",
                        10,
                        true,
                        now.minusDays(30),
                        now.plusYears(1)
                ),

                createPromotion(
                        "WELCOME15",
                        15,
                        true,
                        now.minusDays(30),
                        now.plusYears(1)
                ),

                createPromotion(
                        "VIP20",
                        20,
                        true,
                        now.minusDays(30),
                        now.plusYears(1)
                ),

                createPromotion(
                        "BLACKFRIDAY30",
                        30,
                        false,
                        now.minusDays(30),
                        now.minusDays(1)
                )
        );

        promotionRepository.saveAll(promotions);

        System.out.println(
                "✅ Promotions initialized: "
                        + promotions.size()
        );

    }

    private Promotion createPromotion(
            String code,
            Integer discount,
            boolean active,
            LocalDateTime start,
            LocalDateTime end) {

        Promotion promotion = new Promotion();

        promotion.setCode(code);
        promotion.setDiscountPercentage(discount);
        promotion.setActive(active);
        promotion.setStartTime(start);
        promotion.setEndTime(end);

        return promotion;
    }
}
