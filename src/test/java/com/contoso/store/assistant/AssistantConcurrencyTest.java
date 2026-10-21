package com.contoso.store.assistant;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantConcurrencyTest {

    @Test
    void admitsTwoQuestionsAndRejectsAThirdWithoutQueuingIt() throws Exception {
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        ChatModel model = prompt -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test interrupted", exception);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Done"))));
        };
        var service = new AssistantService(ChatClient.create(model), ObservationRegistry.NOOP);
        var first = CompletableFuture.supplyAsync(() -> service.answer("First"));
        var second = CompletableFuture.supplyAsync(() -> service.answer("Second"));
        try {
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.answer("Third")).isInstanceOf(AssistantException.class)
                    .extracting("code").isEqualTo("busy");
            release.countDown();
            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo("Done");
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("Done");
        }
        finally {
            release.countDown();
            service.close();
        }
    }
}
