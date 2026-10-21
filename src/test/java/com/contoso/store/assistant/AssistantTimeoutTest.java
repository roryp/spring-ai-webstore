package com.contoso.store.assistant;

import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantTimeoutTest {

    @Test
    void cancelsTheProviderWhenTheWholeRequestDeadlineExpires() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        ChatModel model = prompt -> {
            try {
                Thread.sleep(5000);
                throw new AssertionError("The request should have been interrupted");
            }
            catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted");
            }
        };
        var service = new AssistantService(ChatClient.create(model), ObservationRegistry.NOOP, Duration.ofMillis(150));
        try {
            assertThatThrownBy(() -> service.answer("Find a jacket")).isInstanceOf(AssistantException.class)
                    .extracting("code").isEqualTo("timeout");
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            service.close();
        }
    }
}
