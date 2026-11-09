package com.contoso.store;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.github.copilot.CopilotClient;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.ToolExecutionStartEvent;
import com.github.copilot.rpc.CopilotClientMode;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.ProviderConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolSet;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Copilot's agent, running inside the app. It works out each answer with our tools, one step at a time. */
@Service
class Assistant {

    /** Each step costs a model call, so the agent may take at most this many steps for one question. */
    static final int MAX_STEPS = 8;

    private final CopilotClient copilot;
    private final SessionConfig settings;

    Assistant(InventoryTools tools, @Value("${AZURE_OPENAI_ENDPOINT}") String endpoint) {
        // Empty mode switches off everything we don't ask for, such as Copilot's tools for files and commands.
        this.copilot = new CopilotClient(new CopilotClientOptions()
                .setMode(CopilotClientMode.EMPTY)
                .setCopilotHome("/tmp/copilot"));

        // The app signs in to Foundry with its managed identity, so there's no key.
        var credential = new DefaultAzureCredentialBuilder().build();
        var foundry = new TokenRequestContext().addScopes("https://ai.azure.com/.default");

        this.settings = new SessionConfig()
                // Our own model in Microsoft Foundry, through its OpenAI-compatible API.
                .setModel("gpt-6-luna")
                .setProvider(new ProviderConfig()
                        .setType("openai")
                        .setBaseUrl(endpoint + "openai/v1/")
                        .setWireApi("responses")
                        .setBearerTokenProvider(args -> credential.getToken(foundry).map(AccessToken::getToken).toFuture())
                        // Keep answers short: this caps each answer's length and cost.
                        .setMaxOutputTokens(300))
                .setSystemMessage(new SystemMessageConfig()
                        .setMode(SystemMessageMode.REPLACE)
                        .setContent("""
                                You are the Trail Store assistant. Only help with questions about the store.
                                Use the tools to find products and their stock, and to find alternatives. Never invent products or facts.
                                Answer in plain text, in two or three short sentences.
                                """))
                // The agent may use our own tools and nothing else, so it's safe to approve its tool calls.
                .setTools(ToolDefinition.fromObject(tools))
                .setAvailableTools(new ToolSet().addCustom("*"))
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL);
    }

    Answer answer(String question) {
        // Each question gets its own session. We close it, then delete it, so no questions stay on disk.
        var session = copilot.createSession(settings).join();
        try (session) {
            // Note each tool the agent calls, in order, so the page can show its steps.
            var toolCalls = new CopyOnWriteArrayList<String>();
            session.on(ToolExecutionStartEvent.class, event -> {
                toolCalls.add(event.getData().toolName() + " " + event.getData().arguments());
                // Too many steps: stop the agent, so it makes no more model calls.
                if (toolCalls.size() > MAX_STEPS) {
                    session.abort();
                }
            });
            try {
                var reply = session.sendAndWait(question).join().getData().content();
                return new Answer(reply, List.copyOf(toolCalls));
            }
            catch (RuntimeException e) {
                // If we stopped the agent ourselves, say why, instead of reporting an error.
                if (toolCalls.size() > MAX_STEPS) {
                    throw new TooManyStepsException();
                }
                throw e;
            }
        }
        finally {
            copilot.deleteSession(session.getSessionId()).join();
        }
    }

    @PreDestroy
    void stop() {
        copilot.close();
    }

    /** We stopped the agent, because it needed more than {@link #MAX_STEPS} steps. */
    static class TooManyStepsException extends RuntimeException {

        TooManyStepsException() {
            super("The agent needed more than " + MAX_STEPS + " steps");
        }
    }
}
