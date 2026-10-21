package com.contoso.store.assistant;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=ai", "spring.ai.openai.api-key=local-test-only", "store.phase=4"
})
@AutoConfigureTracing
@Import(TelemetryIntegrationTest.ExportConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TelemetryIntegrationTest {

    private static final TestModelEndpoint model = new TestModelEndpoint(true);
    private static final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private static final String CODE = "private-test-access-code";
    @Value("${local.server.port}") int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.openai.base-url", model::url);
        properties.add("store.assistant.access-code-hash", () -> HexFormat.of().formatHex(WorkshopAccess.hash(CODE)));
    }

    @Test
    void correlatesRealHttpModelAndToolSpansAndAccountsForTokensWithoutContent() throws Exception {
        exporter.reset();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/assistant/messages"))
                    .header("Content-Type", "application/json")
                    .header("X-Workshop-Code", CODE)
                    .header("Cookie", "SESSION=private-test-cookie")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"private-test-question\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            String traceId = JsonMapper.builder().build().readTree(response.body()).path("traceId").asString();
            assertThat(traceId).matches("[a-f0-9]{32}");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(spans(traceId)).anyMatch(span -> span.getKind() == SpanKind.SERVER));
            List<SpanData> spans = spans(traceId);
            assertThat(spans).anyMatch(span -> "invoke_agent".equals(attribute(span, "gen_ai.operation.name")));
            assertThat(spans).anyMatch(span -> "execute_tool".equals(attribute(span, "gen_ai.operation.name")));
            var models = spans.stream().filter(span -> "chat".equals(attribute(span, "gen_ai.operation.name"))).toList();
            assertThat(models).hasSize(2);
            assertThat(models.stream().mapToLong(span -> Long.parseLong(attribute(span, "gen_ai.usage.input_tokens"))).sum())
                    .isEqualTo(300);
            assertThat(models.stream().mapToLong(span -> Long.parseLong(attribute(span, "gen_ai.usage.output_tokens"))).sum())
                    .isEqualTo(50);
            assertThat(models).anyMatch(span -> "50".equals(attribute(span, "gen_ai.usage.cache_read.input_tokens")));
            assertThat(model.traceparent.get()).contains(traceId);
            assertThat(spans).allSatisfy(span -> {
                assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                        .isEqualTo("trail-store-phase-4");
                assertThat(span.getAttributes().toString())
                        .doesNotContain(CODE, "private-test-cookie", "private-test-question");
            });
            assertThat(spans.stream().filter(span -> attribute(span, "gen_ai.operation.name") != null))
                    .allSatisfy(span -> assertThat(attribute(span, "gen_ai.agent.id")).isEqualTo("trail-store-assistant"));
        }
    }

    private List<SpanData> spans(String traceId) {
        return exporter.getFinishedSpanItems().stream().filter(span -> span.getTraceId().equals(traceId)).toList();
    }

    private String attribute(SpanData span, String name) {
        return span.getAttributes().get(AttributeKey.stringKey(name));
    }

    @AfterAll
    static void stopModel() {
        model.close();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExportConfiguration {
        @Bean
        SdkTracerProviderBuilderCustomizer captureSpans() {
            return builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        }
    }
}
