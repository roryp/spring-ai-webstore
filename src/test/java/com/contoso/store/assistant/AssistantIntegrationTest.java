package com.contoso.store.assistant;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AssistantIntegrationTest.StubConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantIntegrationTest {

    static final String CODE = "test-only-workshop-code";
    @Autowired MockMvc mvc;
    @Autowired StubModel model;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry properties) {
        properties.add("store.assistant.enabled", () -> true);
        properties.add("store.assistant.access-code-hash", () -> HexFormat.of().formatHex(WorkshopAccess.hash(CODE)));
    }

    @BeforeEach
    void resetModel() {
        model.calls.set(0);
        model.loop = false;
        model.fail = false;
        model.sawStock = false;
    }

    @Test
    void executesTheRealInventoryToolAndPassesItsFactsBackToTheModel() throws Exception {
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", CODE)
                        .contentType("application/json").content("{\"message\":\"Is JKT-003 in stock?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("JKT-003 is out of stock. Its price is USD 149.00."))
                .andExpect(jsonPath("$.requestId").isString());
        assertThat(model.calls.get()).isEqualTo(2);
        assertThat(model.sawStock).isTrue();
    }

    @Test
    void runawayToolCallsStopAfterThreeLookups() throws Exception {
        model.loop = true;
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", CODE)
                        .contentType("application/json").content("{\"message\":\"Find more gear\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("tool_limit"));
        assertThat(model.calls.get()).isEqualTo(4);
    }

    @Test
    void rejectsUnauthorizedRequestsBeforeCallingTheModel() throws Exception {
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", "wrong")
                        .contentType("application/json").content("{\"message\":\"Find a jacket\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(model.calls.get()).isZero();
    }

    @Test
    void enforcesBothMessageAndBodyLimits() throws Exception {
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", CODE)
                        .contentType("application/json").content("{\"message\":\"" + "x".repeat(601) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", CODE)
                        .contentType("application/json").content("{\"message\":\"" + "x".repeat(4096) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
        assertThat(model.calls.get()).isZero();
    }

    @Test
    void reportsProviderFailureWithoutLeakingItsMessage() throws Exception {
        model.fail = true;
        mvc.perform(post("/api/assistant/messages").header("X-Workshop-Code", CODE)
                        .contentType("application/json").content("{\"message\":\"Find a jacket\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider_unavailable"))
                .andExpect(content().string(not(containsString("private-provider-detail"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfiguration {
        @Bean StubModel stubModel() {
            return new StubModel();
        }
    }

    static class StubModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean loop;
        volatile boolean fail;
        volatile boolean sawStock;

        @Override
        public OpenAiChatOptions getOptions() {
            return OpenAiChatOptions.builder().model("chat").build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            int call = calls.incrementAndGet();
            if (fail) throw new IllegalStateException("private-provider-detail");
            if (call == 1 || loop) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("lookup-" + call,
                                "function", "getProduct", "{\"sku\":\"JKT-003\"}")))
                        .build())));
            }
            sawStock = prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast)
                    .flatMap(message -> message.getResponses().stream())
                    .anyMatch(response -> response.responseData().contains("\"stock\":0"));
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("JKT-003 is out of stock. Its price is USD 149.00."))));
        }
    }
}
