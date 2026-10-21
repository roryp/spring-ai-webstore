package com.contoso.store.assistant;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "store.assistant.enabled", havingValue = "true")
public class AssistantConfiguration {

    @Bean
    ChatClient inventoryChat(ChatClient.Builder builder, InventoryTools tools, ObservationRegistry registry) {
        var manager = ToolCallingManager.builder()
                .observationRegistry(registry)
                .maxCallsPerTool(3)
                .maxTotalToolCalls(3)
                .toolExecutionExceptionProcessor(
                        DefaultToolExecutionExceptionProcessor.builder().alwaysThrow(true).build())
                .build();
        return builder
                .defaultSystem(new ClassPathResource("prompts/assistant.txt"))
                .defaultTools(tools)
                .defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(manager).build())
                .build();
    }
}
