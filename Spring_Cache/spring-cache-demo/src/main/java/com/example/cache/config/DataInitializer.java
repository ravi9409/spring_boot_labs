package com.example.cache.config;

import com.example.cache.model.Product;
import com.example.cache.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        List<Product> products = List.of(
            Product.builder().name("MacBook Pro 16\"").sku("MBP-16-2024")
                    .description("Apple M3 Max, 36GB RAM").category("Laptops")
                    .price(new BigDecimal("2499.99")).stockQuantity(150).build(),
            Product.builder().name("iPhone 15 Pro").sku("IPH-15P-256")
                    .description("A17 Pro chip, 256GB").category("Phones")
                    .price(new BigDecimal("999.99")).stockQuantity(500).build(),
            Product.builder().name("AirPods Pro 2").sku("APP-2-USB-C")
                    .description("USB-C, Adaptive Audio").category("Audio")
                    .price(new BigDecimal("249.99")).stockQuantity(1000).build(),
            Product.builder().name("Dell XPS 15").sku("DXPS-15-2024")
                    .description("Intel i9, 32GB RAM").category("Laptops")
                    .price(new BigDecimal("1899.99")).stockQuantity(200).build(),
            Product.builder().name("Samsung Galaxy S24").sku("SGS-24-256")
                    .description("Snapdragon 8 Gen 3").category("Phones")
                    .price(new BigDecimal("849.99")).stockQuantity(350).build(),
            Product.builder().name("Sony WH-1000XM5").sku("SONY-XM5-BLK")
                    .description("Noise cancelling headphones").category("Audio")
                    .price(new BigDecimal("349.99")).stockQuantity(600).build()
        );

        productRepository.saveAll(products);
        log.info("📦 Loaded {} demo products", products.size());
    }
}
