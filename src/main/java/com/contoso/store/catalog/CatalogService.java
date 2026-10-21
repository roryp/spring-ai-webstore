package com.contoso.store.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CatalogService {

    private final List<Product> products;

    public CatalogService(JsonMapper mapper) {
        try (var input = new ClassPathResource("inventory.json").getInputStream()) {
            products = List.copyOf(Arrays.asList(mapper.readValue(input, Product[].class)));
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Cannot load the store inventory", exception);
        }
        Assert.isTrue(!products.isEmpty(), "The inventory must not be empty");
        Assert.isTrue(products.stream().map(Product::sku).distinct().count() == products.size(),
                "Inventory SKUs must be unique");
    }

    public List<Product> search(String query, String category, BigDecimal maxPrice, boolean inStockOnly) {
        Assert.isTrue(maxPrice == null || maxPrice.signum() >= 0, "Maximum price cannot be negative");
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return products.stream()
                .filter(product -> term.isEmpty() || searchableText(product).contains(term))
                .filter(product -> category == null || category.isBlank()
                        || product.category().equalsIgnoreCase(category.strip()))
                .filter(product -> maxPrice == null || product.price().compareTo(maxPrice) <= 0)
                .filter(product -> !inStockOnly || product.inStock())
                .toList();
    }

    public Optional<Product> find(String sku) {
        return products.stream().filter(product -> product.sku().equalsIgnoreCase(sku)).findFirst();
    }

    public List<String> categories() {
        return products.stream().map(Product::category).distinct().sorted().toList();
    }

    private String searchableText(Product product) {
        return (product.sku() + " " + product.name() + " " + product.description())
                .toLowerCase(Locale.ROOT);
    }
}
