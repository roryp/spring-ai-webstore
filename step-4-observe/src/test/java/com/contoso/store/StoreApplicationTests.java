package com.contoso.store;

import java.util.List;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the app with a fake AI model, so these tests never call Azure. */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "AZURE_OPENAI_ENDPOINT=https://test.openai.azure.com/"})
@AutoConfigureMockMvc
class StoreApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryTools tools;

    @Test
    void homePageShowsProductsAndStock() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trail Rain Jacket")))
                .andExpect(content().string(containsString("$99")))
                .andExpect(content().string(containsString("Out of stock")));
    }

    @Test
    void askShowsTheModelsAnswer() throws Exception {
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
    void theToolReturnsTheRealCatalog() {
        assertThat(tools.listProducts()).hasSize(6)
                .anyMatch(product -> product.sku().equals("JKT-003") && product.stock() == 0);
    }

    @Test
    void askingAQuestionCreatesOneTrace() throws Exception {
        InMemorySpans.EXPORTER.reset();

        mvc.perform(post("/ask").param("question", "Which jackets are in stock?"));

        List<SpanData> spans = InMemorySpans.EXPORTER.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName).contains("http post /ask", "spring_ai chat_client");
        assertThat(spans).extracting(SpanData::getTraceId).containsOnly(spans.getFirst().getTraceId());
    }

    @TestConfiguration
    static class FakeModel {

        @Bean
        ChatModel chatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("A fake answer"))));
        }
    }

    /** Keeps finished spans in memory so a test can check them. */
    @TestConfiguration
    static class InMemorySpans {

        static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();

        @Bean
        SdkTracerProviderBuilderCustomizer keepSpansInMemory() {
            return builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(EXPORTER));
        }
    }
}
