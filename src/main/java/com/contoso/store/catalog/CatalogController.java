package com.contoso.store.catalog;

import com.contoso.store.StoreProperties;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CatalogController {

    private final CatalogService catalog;
    private final StoreProperties store;

    public CatalogController(CatalogService catalog, StoreProperties store) {
        this.catalog = catalog;
        this.store = store;
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "") @Size(max = 100) String q,
                        @RequestParam(defaultValue = "") @Size(max = 40) String category,
                        @RequestParam(defaultValue = "false") boolean inStock,
                        Model model) {
        model.addAttribute("products", catalog.search(q, category, null, inStock));
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("query", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("inStockOnly", inStock);
        model.addAttribute("store", store);
        return "index";
    }
}
