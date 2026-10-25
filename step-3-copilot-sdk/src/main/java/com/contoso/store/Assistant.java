package com.contoso.store;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.github.copilot.CopilotClient;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.CopilotClientMode;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.ProviderConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Copilot's agent, running inside the app. It answers questions about the store. */
@Service
class Assistant {

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
                                Call list_products to get products, prices, and stock. Never invent products.
                                Answer in plain text, in two or three short sentences.
                                """))
                // The agent may use our one tool and nothing else, so it's safe to approve its tool calls.
                .setTools(ToolDefinition.fromObject(tools))
                .setAvailableTools(List.of("custom:list_products"))
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL);
    }

    String answer(String question) {
        // Each question gets its own session. We close it, then delete it, so no questions stay on disk.
        var session = copilot.createSession(settings).join();
        try (session) {
            return session.sendAndWait(question).join().getData().content();
        }
        finally {
            copilot.deleteSession(session.getSessionId()).join();
        }
    }

    @PreDestroy
    void stop() {
        copilot.close();
    }
}
