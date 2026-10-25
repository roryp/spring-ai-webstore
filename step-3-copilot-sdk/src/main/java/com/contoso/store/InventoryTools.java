package com.contoso.store;

import com.github.copilot.tool.CopilotTool;
import java.util.List;
import org.springframework.stereotype.Component;

/** Methods the AI model may call. They read the same catalog as the web page. */
@Component
class InventoryTools {

    private final Catalog catalog;

    InventoryTools(Catalog catalog) {
        this.catalog = catalog;
    }

    @CopilotTool("List every product in the store with its SKU, name, price in US dollars, and units in stock")
    List<Product> listProducts() {
        return catalog.all();
    }
}
