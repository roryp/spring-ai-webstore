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

    private final Catalog catalog;

    InventoryTools(Catalog catalog) {
        this.catalog = catalog;
    }

    // Find the products whose name or SKU contains every word, with their stock.
    @CopilotTool("Search the store's products by name or SKU. Returns the SKU, name, price in US dollars, and units in stock of each match")
    List<Product> searchProducts(@CopilotToolParam("Words to look for in product names or SKUs, such as waterproof jacket or JKT-003") String text) {
        var words = text.toLowerCase().split("\\s+");
        return catalog.all().stream()
                .filter(product -> {
                    var searchable = (product.sku() + " " + product.name()).toLowerCase();
                    return Arrays.stream(words).allMatch(searchable::contains);
                })
                .toList();
    }

    // Suggest in-stock products of the same kind, closest in price first.
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

    // Look up one product by its SKU, or fail with a clear message.
    private Product product(String sku) {
        return catalog.all().stream()
                .filter(product -> product.sku().equalsIgnoreCase(sku))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No product has the SKU " + sku));
    }
}
