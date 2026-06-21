package com.ordering.cartservice.client;

import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service")
public interface ProductClient {

    @GetMapping("/api/catalog/products/{id}")
    ApiResponse<ProductDTO> getProduct(@PathVariable("id") Long id);
}
