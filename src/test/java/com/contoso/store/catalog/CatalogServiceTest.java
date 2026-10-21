package com.contoso.store.catalog;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogServiceTest {

    private final CatalogService catalog = new CatalogService(JsonMapper.builder().build());

    @Test
    void loadsTwelveUniqueProducts() {
        var products = catalog.search("", "", null, false);
        assertThat(products).hasSize(12);
        assertThat(products).extracting(Product::sku).doesNotHaveDuplicates();
    }

    @Test
    void findsOnlyInStockWaterproofJacketsUnder150() {
        assertThat(catalog.search(" waterproof ", "Jackets", new BigDecimal("150"), true))
                .extracting(Product::sku).containsExactly("JKT-001", "JKT-002");
    }

    @Test
    void returnsAuthoritativeStockAndPrice() {
        Product product = catalog.find("jkt-003").orElseThrow();
        assertThat(product.price()).isEqualByComparingTo("149.00");
        assertThat(product.stock()).isZero();
        assertThat(product.inStock()).isFalse();
    }

    @Test
    void unknownProductsAndSearchesDoNotInventResults() {
        assertThat(catalog.find("missing")).isEmpty();
        assertThat(catalog.search("spaceship", "", null, false)).isEmpty();
    }

    @Test
    void rejectsNegativePriceFilter() {
        assertThatThrownBy(() -> catalog.search("", "", new BigDecimal("-1"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
