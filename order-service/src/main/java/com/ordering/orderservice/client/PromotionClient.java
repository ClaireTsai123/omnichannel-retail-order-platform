package com.ordering.orderservice.client;

import com.ordering.common.domain.OrderSource;
import com.ordering.common.dto.PromotionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "promotion-service")
public interface PromotionClient {

    @GetMapping("/api/promotions/{code}")
    PromotionResponse validatePromotion(@PathVariable String code, @RequestParam OrderSource source);

}
