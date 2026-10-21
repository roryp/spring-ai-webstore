package com.contoso.store.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
public class AssistantObservations {

    public static final String AGENT_ID = "trail-store-assistant";

    @Bean
    ObservationFilter assistantIdentity() {
        return context -> {
            if (belongsToAssistant(context)) {
                context.addLowCardinalityKeyValue(KeyValue.of("gen_ai.agent.id", AGENT_ID));
                context.addLowCardinalityKeyValue(KeyValue.of("gen_ai.agent.name", AGENT_ID));
            }
            return context;
        };
    }

    private boolean belongsToAssistant(Observation.ContextView context) {
        Observation.ContextView current = context;
        while (current != null) {
            String name = current.getName();
            if (name.equals("invoke_agent") || name.startsWith("spring.ai.") || name.startsWith("gen_ai.")) {
                return true;
            }
            if (current instanceof ServerRequestObservationContext server
                    && server.getCarrier().getRequestURI().equals("/api/assistant/messages")) {
                return true;
            }
            var parent = current.getParentObservation();
            current = parent == null ? null : parent.getContextView();
        }
        return false;
    }
}
