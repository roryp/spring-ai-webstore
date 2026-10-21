package com.contoso.store.assistant;

import com.contoso.store.catalog.CatalogService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryToolsTest {

    private final InventoryTools tools = new InventoryTools(new CatalogService(JsonMapper.builder().build()));

    @Test
    void boundsToolResults() {
        assertThat(tools.searchProducts("", "", null, false)).hasSize(5);
    }

    @Test
    void toolsUseTheSamePriceAndStockAsTheCatalog() {
        var jackets = tools.searchProducts("waterproof", "Jackets", new BigDecimal("150"), true);
        assertThat(jackets).extracting(InventoryTools.ProductFact::sku).containsExactly("JKT-001", "JKT-002");
        assertThat(jackets.getFirst().price()).isEqualByComparingTo("99.00");
        assertThat(jackets.getFirst().currency()).isEqualTo("USD");
        assertThat(tools.getProduct("JKT-003").product().stock()).isZero();
        assertThat(tools.getProduct("JKT-003").alternatives()).extracting(InventoryTools.ProductFact::sku)
                .containsExactly("JKT-001", "JKT-002");
    }

    @Test
    void unknownProductsAreExplicitlyNotFound() {
        var lookup = tools.getProduct("NO-SUCH-SKU");
        assertThat(lookup.found()).isFalse();
        assertThat(lookup.product()).isNull();
    }

    @Test
    void invalidToolInputsDoNotGetSilentlyAccepted() {
        assertThatThrownBy(() -> tools.searchProducts("x".repeat(101), "", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.searchProducts("", "", new BigDecimal("-1"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
