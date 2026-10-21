package com.contoso.store.assistant;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
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

    private static final AtomicReference<String> path = new AtomicReference<>();
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final HttpServer server = startServer();
    @Autowired ChatModel model;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        properties.add("spring.ai.openai.microsoft-deployment-name", () -> "inventory-demo");
        properties.add("spring.ai.openai.chat.model", () -> "inventory-demo");
        properties.add("store.assistant.access-code-hash",
                () -> HexFormat.of().formatHex(WorkshopAccess.hash("test-only")));
    }

    @Test
    void sendsTheDeploymentNameOnTheWireWithoutAzureCredentials() {
        var response = model.call(new Prompt("hello"));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("Test answer");
        assertThat(path.get()).contains("/deployments/inventory-demo/");
        assertThat(body.get()).contains("\"model\":\"inventory-demo\"");
    }

    static HttpServer startServer() {
        try {
            HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                        {"id":"test","object":"chat.completion","created":1,"model":"underlying-test-model",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"Test answer"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":3,"total_tokens":13}}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            stub.start();
            return stub;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot start the local model test endpoint", exception);
        }
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }
}
