package com.contoso.store;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class StoreController {

    private final Catalog catalog;

    StoreController(Catalog catalog) {
        this.catalog = catalog;
    }

    // Handle the GET request for the home page, showing the six products.
    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("products", catalog.all());
        return "index";
    }
}
