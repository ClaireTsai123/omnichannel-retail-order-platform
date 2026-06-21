package com.ordering.catalogservice.service;

import com.ordering.common.dto.ProductDTO;
import com.ordering.common.exception.ResourceNotFoundException;
import com.ordering.catalogservice.entity.Product;
import com.ordering.catalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    @Override
    @Cacheable(value = "menu:all")
    public List<ProductDTO> getAllAvailableProducts() {
        System.out.println(".....hitting db......");
        return productRepository.findByActiveTrue().stream()
                .map(this::convertToDTO).toList();
    }


    @Cacheable(value = "menu:all", key = "#id")
    @Override
    public ProductDTO getProductById(Long id) {
        Product item = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        return convertToDTO(item);
    }

    @Override
    public ProductDTO getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku).orElseThrow(() ->
                new ResourceNotFoundException("Product item not found with sku: " + sku));
        return convertToDTO(product);
    }

    @Override
    public List<ProductDTO> getProductsByCategory(String category) {
        return productRepository.findByCategory(category.toLowerCase()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "menu:all", allEntries = true)
    @Override
    public ProductDTO createProduct(Product item) {
        Product saved = productRepository.save(item);
        return convertToDTO(saved);
    }

     @CacheEvict(value = "menu:all", allEntries = true)
    @Override
    public ProductDTO updateProduct(Long id, Product product) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product item not found"));
        existing.setSku(product.getSku());
        existing.setBrand(product.getBrand());
        existing.setProductName(product.getProductName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        existing.setCategory(product.getCategory());
        existing.setImageUrl(product.getImageUrl());
        existing.setActive(product.getActive());
        Product updated = productRepository.save(existing);
        return convertToDTO(updated);
    }

    @CacheEvict(value = "menu:all", allEntries = true)
    @Override
    public void deleteProduct(Long id) {
        productRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Product item not found")
        );
        productRepository.deleteById(id);
    }

    private ProductDTO convertToDTO(Product item) {
        ProductDTO dto = new ProductDTO();
        dto.setId(item.getId());
        dto.setSku(item.getSku());
        dto.setBrand(item.getBrand());
        dto.setProductName(item.getProductName());
        dto.setDescription(item.getDescription());
        dto.setPrice(item.getPrice());
        dto.setCategory(item.getCategory());
        dto.setImageUrl(item.getImageUrl());
        dto.setActive(item.getActive());
        return dto;
    }

}
