package com.contoso.store;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the app with a fake assistant, so these tests never start Copilot's runtime or call Azure. */
@SpringBootTest
@AutoConfigureMockMvc
class StoreApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryTools tools;

    @MockitoBean
    Assistant assistant;

    @Test
    void homePageShowsProductsAndStock() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trail Rain Jacket")))
                .andExpect(content().string(containsString("$99")))
                .andExpect(content().string(containsString("Out of stock")));
    }

    @Test
    void askShowsTheAssistantsAnswer() throws Exception {
        given(assistant.answer(anyString())).willReturn("A fake answer");

        mvc.perform(post("/ask").param("question", "Which jackets are in stock?"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A fake answer")))
                .andExpect(content().string(containsString("Trail Rain Jacket")));
    }

    @Test
    void askRejectsVeryLongQuestions() throws Exception {
        mvc.perform(post("/ask").param("question", "x".repeat(301)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("up to 300 characters")));
    }

    @Test
    void askSaysSoWhenTheAssistantFails() throws Exception {
        given(assistant.answer(anyString())).willThrow(new IllegalStateException("The model is not reachable"));

        mvc.perform(post("/ask").param("question", "Which jackets are in stock?"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("could not answer right now")));
    }

    @Test
    void theToolReturnsTheRealCatalog() {
        assertThat(tools.listProducts()).hasSize(6)
                .anyMatch(product -> product.sku().equals("JKT-003") && product.stock() == 0);
    }

    @Test
    void copilotToolTurnsTheMethodIntoTheListProductsTool() {
        assertThat(ToolDefinition.fromObject(tools)).extracting(ToolDefinition::name).containsExactly("list_products");
    }
}
