package com.contoso.store.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CatalogApi {

    private final CatalogService catalog;

    public CatalogApi(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/products")
    public List<Product> products(@RequestParam(defaultValue = "") @Size(max = 100) String q,
                                  @RequestParam(defaultValue = "") @Size(max = 40) String category,
                                  @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
                                  @RequestParam(defaultValue = "false") boolean inStock) {
        return catalog.search(q, category, maxPrice, inStock);
    }

    @GetMapping("/api/products/{sku}")
    public Product product(@PathVariable String sku) {
        return catalog.find(sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
