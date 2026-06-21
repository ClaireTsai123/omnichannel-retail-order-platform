package com.ordering.catalogservice.util;

import com.ordering.catalogservice.entity.Product;
import com.ordering.catalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductDataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        List<Product> items = List.of(

                // ── SKINCARE ──────────────────────────────────────────
                new Product(null, "SKC-001", "Hydro Boost Water Gel", "Neutrogena", "SKINCARE",
                        "Lightweight gel moisturizer with hyaluronic acid for 48-hour hydration.",
                        new BigDecimal("24.99"), "https://example.com/hydro-boost.jpg", true, null, null),

                new Product(null, "SKC-002", "Vitamin C Brightening Serum", "CeraVe", "SKINCARE",
                        "10% pure Vitamin C serum to reduce dark spots and even skin tone.",
                        new BigDecimal("34.99"), "https://example.com/vitamin-c-serum.jpg", true, null, null),

                new Product(null, "SKC-003", "Ultra Facial Cream SPF 30", "Kiehl's", "SKINCARE",
                        "Daily moisturizer with broad-spectrum SPF 30 for all skin types.",
                        new BigDecimal("42.00"), "https://example.com/ultra-facial-cream.jpg", true, null, null),

                // ── MAKEUP ────────────────────────────────────────────
                new Product(null, "MKP-001", "Fit Me Matte + Poreless Foundation", "Maybelline", "MAKEUP",
                        "Lightweight foundation that controls shine for a natural matte finish. 40 shades.",
                        new BigDecimal("11.99"), "https://example.com/fitme-foundation.jpg", true, null, null),

                new Product(null, "MKP-002", "Lip Comfort Oil", "Clarins", "MAKEUP",
                        "Sheer tinted lip oil that nourishes and plumps with a glossy finish.",
                        new BigDecimal("29.00"), "https://example.com/lip-comfort-oil.jpg", true, null, null),

                new Product(null, "MKP-003", "Lash Sensational Sky High Mascara", "Maybelline", "MAKEUP",
                        "Lengthening and volumizing mascara with flexible fiber brush.",
                        new BigDecimal("13.99"), "https://example.com/sky-high-mascara.jpg", true, null, null),

                // ── HAIRCARE ──────────────────────────────────────────
                new Product(null, "HRC-001", "Scalp Revival Charcoal Shampoo", "Briogeo", "HAIRCARE",
                        "Micro-exfoliating charcoal shampoo that removes buildup and soothes dry scalp.",
                        new BigDecimal("38.00"), "https://example.com/scalp-revival-shampoo.jpg", true, null, null),

                new Product(null, "HRC-002", "Argan Oil of Morocco Conditioner", "OGX", "HAIRCARE",
                        "Nourishing conditioner infused with argan oil for smooth, shiny hair.",
                        new BigDecimal("9.97"), "https://example.com/argan-oil-conditioner.jpg", true, null, null),

                // ── FRAGRANCE ─────────────────────────────────────────
                new Product(null, "FRG-001", "Black Opium Eau de Parfum", "YSL Beauty", "FRAGRANCE",
                        "Bold scent with notes of black coffee, white flowers, and vanilla.",
                        new BigDecimal("104.00"), "https://example.com/black-opium.jpg", true, null, null),

                new Product(null, "FRG-002", "Cloud Eau de Parfum", "Ariana Grande", "FRAGRANCE",
                        "Dreamy fragrance with lavender blossom, pear, and cashmere woods.",
                        new BigDecimal("68.00"), "https://example.com/cloud-edp.jpg", true, null, null),
                new Product(null, "FRG-003", "Daisy Eau de Toilette", "Marc Jacobs", "FRAGRANCE",
                        "Fresh and feminine scent with wild strawberry, violet leaves, and jasmine.",
                        new BigDecimal("85.00"), "https://example.com/daisy-edt.jpg", true, null, null),

                // ── TOOLS ─────────────────────────────────────────────
                new Product(null, "TLB-001", "Kabuki Foundation Brush", "Real Techniques", "TOOLS",
                        "Flat-top kabuki brush for seamless full-coverage foundation application.",
                        new BigDecimal("14.99"), "https://example.com/kabuki-brush.jpg", true, null, null),

                new Product(null, "TLB-002", "Jade Facial Roller", "Mount Lai", "TOOLS",
                        "Dual-ended jade roller to reduce puffiness and improve circulation.",
                        new BigDecimal("28.00"), "https://example.com/jade-roller.jpg", false, null, null),

                new Product( null, "TLB-003", "Silicone Makeup Sponge", "Beautyblender", "TOOLS",
                        "Non-porous silicone sponge for flawless foundation application and easy cleaning.",
                        new BigDecimal("19.99"), "https://example.com/silicone-sponge.jpg", true, null, null)
        );

        productRepository.saveAll(items);
        System.out.println("✅ " + items.size() + " Sephora-style products initialized.");
    }
}

