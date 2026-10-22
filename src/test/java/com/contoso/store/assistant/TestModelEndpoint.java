package com.contoso.store.assistant;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.json.JsonMapper;

final class TestModelEndpoint implements AutoCloseable {

    private final HttpServer server;
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicReference<String> traceparent = new AtomicReference<>();

    TestModelEndpoint(boolean callInventoryTool) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                body.set(request);
                boolean hasToolResult = false;
                for (var message : JsonMapper.builder().build().readTree(request).path("messages")) {
                    if ("tool".equals(message.path("role").asString())) hasToolResult = true;
                }
                boolean firstRound = callInventoryTool && !hasToolResult;
                String choice = firstRound ? """
                        {"index":0,"message":{"role":"assistant","content":null,
                         "tool_calls":[{"id":"lookup-1","type":"function","function":{"name":"getProduct","arguments":"{\\"sku\\":\\"JKT-003\\"}"}}]},
                         "finish_reason":"tool_calls"}
                        """ : """
                        {"index":0,"message":{"role":"assistant","content":"Test answer"},"finish_reason":"stop"}
                        """;
                String usage = firstRound
                        ? "\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120"
                        : "\"prompt_tokens\":200,\"completion_tokens\":30,\"total_tokens\":230,\"prompt_tokens_details\":{\"cached_tokens\":50}";
                byte[] response = ("""
                        {"id":"test","object":"chat.completion","created":1,"model":"underlying-test-model",
                         "choices":[%s],"usage":{%s}}
                        """.formatted(choice, usage)).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot start the loopback model test endpoint", exception);
        }
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
