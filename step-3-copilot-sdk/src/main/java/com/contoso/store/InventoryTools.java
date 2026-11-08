package com.contoso.store;

import com.github.copilot.tool.CopilotTool;
import com.github.copilot.tool.CopilotToolParam;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** The agent's tools. Each one does one small job, and they only read the catalog. */
@Component
class InventoryTools {

    /** A search result: a product's SKU, name, and price, but not its stock. */
    record Match(String sku, String name, int price) {
    }

    private final Catalog catalog;

    InventoryTools(Catalog catalog) {
        this.catalog = catalog;
    }

    @CopilotTool("Search the store's products by name. Returns the SKU, name, and price in US dollars of each match, but not the stock")
    List<Match> searchProducts(@CopilotToolParam("Words to look for in product names, such as waterproof jacket") String text) {
        var words = text.toLowerCase().split("\\s+");
        return catalog.all().stream()
                .filter(product -> Arrays.stream(words).allMatch(product.name().toLowerCase()::contains))
                .map(product -> new Match(product.sku(), product.name(), product.price()))
                .toList();
    }

    @CopilotTool("Check how many units of one product are in stock")
    int checkStock(@CopilotToolParam("The product's SKU, such as JKT-001") String sku) {
        return product(sku).stock();
    }

    @CopilotTool("Find other products of the same kind that are in stock, closest in price first. Returns their SKU, name, price in US dollars, and units in stock")
    List<Product> findAlternatives(@CopilotToolParam("The SKU of the product to replace, such as JKT-003") String sku) {
        var original = product(sku);
        // Products of the same kind share the start of their SKU, such as JKT for jackets.
        var kind = original.sku().substring(0, 3);
        return catalog.all().stream()
                .filter(product -> product.sku().startsWith(kind) && !product.equals(original) && product.stock() > 0)
                .sorted(Comparator.comparingInt(product -> Math.abs(product.price() - original.price())))
                .toList();
    }

    private Product product(String sku) {
        return catalog.all().stream()
                .filter(product -> product.sku().equalsIgnoreCase(sku))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No product has the SKU " + sku));
    }
}
