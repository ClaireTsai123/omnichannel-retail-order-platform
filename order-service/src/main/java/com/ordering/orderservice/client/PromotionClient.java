package com.ordering.orderservice.client;

import com.ordering.common.dto.PromotionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "promotion-service")
public interface PromotionClient {

    @GetMapping("/api/promotions/{code}")
    PromotionResponse validatePromotion(@PathVariable String code);

}
