package com.ordering.catalogservice.controller;

import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.ProductDTO;
import com.ordering.catalogservice.entity.Product;
import com.ordering.catalogservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalog/products")
public class ProductController {
    private final ProductService productService;

    @GetMapping
    //@Cacheable(value = "menu:all")
    public ApiResponse<List<ProductDTO>> getAllProducts() {
        //System.out.println(">>> HITTING DB <<<");
        return ApiResponse.success(productService.getAllAvailableProducts());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDTO> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductById(id));
    }

    @GetMapping("/sku/{sku}")
    public ApiResponse<ProductDTO> getProductBySku(@PathVariable String sku) {
        return ApiResponse.success(productService.getProductBySku(sku));
    }

    @GetMapping("/category")//GET /api/catalog/products/category?category=makeup
    public ApiResponse<List<ProductDTO>> getProductsByCategory(@RequestParam("category") String category) {
        return ApiResponse.success(productService.getProductsByCategory(category));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ApiResponse<ProductDTO> createItem(@Valid @RequestBody Product product) {
        return ApiResponse.success(productService.createProduct(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ApiResponse<ProductDTO> updateItem(@PathVariable Long id, @RequestBody Product product) {
        return ApiResponse.success(productService.updateProduct(id, product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.success(null);
    }
}
