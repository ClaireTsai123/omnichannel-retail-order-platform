package com.ordering.catalogservice.service;

import com.ordering.common.dto.ProductDTO;
import com.ordering.catalogservice.entity.Product;

import java.util.List;

public interface ProductService {

    List<ProductDTO> getAllAvailableProducts();

    ProductDTO getProductById(Long id);

    ProductDTO getProductBySku(String sku);

    ProductDTO createProduct(Product item);

    ProductDTO updateProduct(Long id, Product item);

    void deleteProduct(Long id);

    List<ProductDTO> getProductsByCategory(String category);
}
