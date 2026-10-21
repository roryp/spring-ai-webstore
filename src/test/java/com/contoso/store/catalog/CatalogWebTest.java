package com.contoso.store.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rendersCatalogWithoutCloudCredentials() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trail Rain Jacket")))
                .andExpect(content().string(containsString("12 products / USD")));
    }

    @Test
    void filtersThePage() throws Exception {
        mvc.perform(get("/").param("category", "Jackets").param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 products / USD")))
                .andExpect(content().string(not(containsString("Summit Waterproof Jacket"))));
    }

    @Test
    void apiReturnsExactCatalogFacts() throws Exception {
        mvc.perform(get("/api/products/JKT-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(149.0))
                .andExpect(jsonPath("$.stock").value(0));
        mvc.perform(get("/api/products").param("category", "Jackets")
                        .param("maxPrice", "150").param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknownSkuReturns404() throws Exception {
        mvc.perform(get("/api/products/missing")).andExpect(status().isNotFound());
    }

    @Test
    void invalidFiltersReturn400() throws Exception {
        mvc.perform(get("/api/products").param("maxPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
    }

    @Test
    void reflectedInputIsEscaped() throws Exception {
        mvc.perform(get("/").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
