package com.contoso.store.assistant;

import java.util.HexFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.profiles.active=ai", "spring.ai.openai.api-key=local-test-only"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ModelRoutingTest {

    private static final TestModelEndpoint server = new TestModelEndpoint(false);
    @Autowired ChatModel model;
    @Autowired AssistantService assistant;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.openai.base-url", server::url);
        properties.add("spring.ai.openai.microsoft-deployment-name", () -> "inventory-demo");
        properties.add("spring.ai.openai.chat.model", () -> "inventory-demo");
        properties.add("store.assistant.access-code-hash",
                () -> HexFormat.of().formatHex(WorkshopAccess.hash("test-only")));
    }

    @Test
    void sendsTheDeploymentNameOnTheWireWithoutAzureCredentials() {
        var response = model.call(new Prompt("hello"));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("Test answer");
        assertThat(server.path.get()).isEqualTo("/openai/v1/chat/completions");
        assertThat(server.body.get()).contains("\"model\":\"inventory-demo\"");
    }

    @Test
    void chatClientPreservesTheConfiguredDeploymentAndOptionsWithTools() {
        assertThat(assistant.answer("Find a jacket")).isEqualTo("Test answer");
        assertThat(server.path.get()).isEqualTo("/openai/v1/chat/completions");
        assertThat(server.body.get()).contains("\"model\":\"inventory-demo\"")
                .contains("\"reasoning_effort\":\"none\"")
                .contains("\"max_completion_tokens\":600");
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }
}
