package com.contoso.store.catalog;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.util.Assert;

public record Product(String sku, String name, String description, String category,
                      BigDecimal price, int stock, String icon) {

    public Product {
        Assert.hasText(sku, "Product SKU is required");
        Assert.hasText(name, "Product name is required");
        Assert.hasText(description, "Product description is required");
        Assert.hasText(category, "Product category is required");
        Assert.isTrue(price != null && price.signum() > 0, "Product price must be positive");
        Assert.isTrue(stock >= 0, "Product stock cannot be negative");
        Assert.isTrue(Set.of("jacket", "boot", "pack", "tent", "gear").contains(icon),
                "Unknown product illustration");
    }

    public boolean inStock() {
        return stock > 0;
    }
}
