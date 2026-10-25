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

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("products", catalog.all());
        return "index";
    }
}
