package com.contoso.store;

import com.github.copilot.rpc.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
                .andExpect(content().string(containsString("Out of stock")))
                .andExpect(content().string(not(containsString("The agent's steps"))));
    }

    @Test
    void askShowsTheAnswerAndTheAgentsSteps() throws Exception {
        given(assistant.answer(anyString())).willReturn(new Answer("A fake answer",
                List.of("search_products {text=waterproof}", "check_stock {sku=JKT-003}")));

        mvc.perform(post("/ask").param("question", "Find me a waterproof jacket that's in stock."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A fake answer")))
                .andExpect(content().string(containsString("The agent's steps")))
                .andExpect(content().string(containsString("search_products {text=waterproof}")))
                .andExpect(content().string(containsString("check_stock {sku=JKT-003}")))
                .andExpect(content().string(containsString("Trail Rain Jacket")));
    }

    @Test
    void askShowsNoStepsWhenTheAgentCalledNoTools() throws Exception {
        given(assistant.answer(anyString())).willReturn(new Answer("I can only help with the store.", List.of()));

        mvc.perform(post("/ask").param("question", "Run the ls command."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("I can only help with the store.")))
                .andExpect(content().string(not(containsString("The agent's steps"))));
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
    void searchFindsProductsWhoseNameHasEveryWord() {
        assertThat(tools.searchProducts("waterproof"))
                .containsExactly(new InventoryTools.Match("JKT-003", "Summit Waterproof Jacket", 149));
        assertThat(tools.searchProducts("summit JACKET")).extracting(InventoryTools.Match::sku).containsExactly("JKT-003");
        assertThat(tools.searchProducts("Jacket")).hasSize(3);
        assertThat(tools.searchProducts("kayak")).isEmpty();
    }

    @Test
    void checkStockReadsTheRealCatalog() {
        assertThat(tools.checkStock("JKT-003")).isZero();
        assertThat(tools.checkStock("JKT-001")).isEqualTo(12);
    }

    @Test
    void checkStockSaysSoWhenTheSkuIsUnknown() {
        assertThatThrownBy(() -> tools.checkStock("KYK-001")).hasMessage("No product has the SKU KYK-001");
    }

    @Test
    void findAlternativesSuggestsInStockProductsOfTheSameKindClosestInPriceFirst() {
        assertThat(tools.findAlternatives("JKT-003")).extracting(Product::sku).containsExactly("JKT-002", "JKT-001");
        assertThat(tools.findAlternatives("BOT-001")).isEmpty();
    }

    @Test
    void copilotToolTurnsTheMethodsIntoTools() {
        assertThat(ToolDefinition.fromObject(tools)).extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder("search_products", "check_stock", "find_alternatives");
    }
}
