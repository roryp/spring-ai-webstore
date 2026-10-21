package com.contoso.store.assistant;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "store.assistant.enabled", havingValue = "true")
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private final ChatClient chat;
    private final ObservationRegistry observations;
    private final Duration timeout;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().name("inventory-chat-", 0).factory());

    @Autowired
    public AssistantService(ChatClient chat, ObservationRegistry observations) {
        this(chat, observations, Duration.ofSeconds(30));
    }

    AssistantService(ChatClient chat, ObservationRegistry observations, Duration timeout) {
        this.chat = chat;
        this.observations = observations;
        this.timeout = timeout;
    }

    public String answer(String question) {
        Observation parent = observations.getCurrentObservation();
        Future<String> request;
        try {
            request = executor.submit(() -> {
                try (var scope = parent == null ? null : parent.openScope()) {
                    return callModel(question);
                }
            });
        }
        catch (RejectedExecutionException exception) {
            throw new AssistantException(HttpStatus.TOO_MANY_REQUESTS, "busy",
                    "Two workshop questions are already running. Please try again shortly.");
        }
        try {
            return request.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException exception) {
            request.cancel(true);
            log.warn("Assistant request timed out");
            throw new AssistantException(HttpStatus.GATEWAY_TIMEOUT, "timeout",
                    "The assistant took too long. Please retry or use the catalog.");
        }
        catch (InterruptedException exception) {
            request.cancel(true);
            Thread.currentThread().interrupt();
            throw new AssistantException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted",
                    "The assistant request was interrupted. Please retry.");
        }
        catch (ExecutionException exception) {
            if (exception.getCause() instanceof AssistantException failure) {
                throw failure;
            }
            log.warn("Assistant provider failed; exceptionType={}", exception.getCause().getClass().getSimpleName());
            throw new AssistantException(HttpStatus.SERVICE_UNAVAILABLE, "provider_unavailable",
                    "The AI provider is unavailable or throttled. Please retry; the catalog still works.");
        }
    }

    private String callModel(String question) {
        var response = chat.prompt().user(question).call().chatResponse();
        if (response == null || response.getResult() == null) {
            throw new AssistantException(HttpStatus.BAD_GATEWAY, "empty_response", "The assistant returned no answer.");
        }
        if (ToolCallLimitExceededException.FINISH_REASON.equals(response.getResult().getMetadata().getFinishReason())) {
            throw new AssistantException(HttpStatus.UNPROCESSABLE_CONTENT, "tool_limit",
                    "That question needed too many lookup steps. Please ask a simpler inventory question.");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new AssistantException(HttpStatus.BAD_GATEWAY, "empty_response", "The assistant returned no answer.");
        }
        return text;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
