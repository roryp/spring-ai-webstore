package com.contoso.store.assistant;

import com.contoso.store.catalog.CatalogService;
import com.contoso.store.catalog.Product;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class InventoryTools {

    private final CatalogService catalog;

    public InventoryTools(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Tool(description = "Search the current inventory. Returns at most five products with exact USD prices and stock. Categories: Jackets, Footwear, Packs, Camp, Gear. Query is a short literal keyword.")
    public List<ProductFact> searchProducts(
            @ToolParam(description = "Short text keyword, or empty for all products") String query,
            @ToolParam(description = "Exact category, or empty for all categories") String category,
            @ToolParam(description = "Maximum price in USD; omit for no price limit", required = false) BigDecimal maxPrice,
            @ToolParam(description = "True to exclude out-of-stock products") boolean inStockOnly) {
        Assert.isTrue(query != null && query.length() <= 100, "Query must be at most 100 characters");
        Assert.isTrue(category != null && category.length() <= 40, "Category must be at most 40 characters");
        return catalog.search(query, category, maxPrice, inStockOnly).stream()
                .limit(5).map(ProductFact::from).toList();
    }

    @Tool(description = "Look up a product's current price and stock by SKU. found=false means the SKU is not in this store.")
    public ProductLookup getProduct(@ToolParam(description = "SKU, such as JKT-003") String sku) {
        Assert.isTrue(sku != null && sku.length() <= 40, "SKU must be at most 40 characters");
        return catalog.find(sku).map(product -> new ProductLookup(sku, true, ProductFact.from(product)))
                .orElseGet(() -> new ProductLookup(sku, false, null));
    }

    public record ProductFact(String sku, String name, String category, BigDecimal price,
                              String currency, int stock) {
        static ProductFact from(Product product) {
            return new ProductFact(product.sku(), product.name(), product.category(),
                    product.price(), "USD", product.stock());
        }
    }

    public record ProductLookup(String sku, boolean found, ProductFact product) { }
}
