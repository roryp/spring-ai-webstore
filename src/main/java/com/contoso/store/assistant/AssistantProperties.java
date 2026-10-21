package com.contoso.store.assistant;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("store.assistant")
public record AssistantProperties(boolean enabled, String accessCodeHash) {

    @AssertTrue(message = "AI requires a SHA-256 workshop access-code hash; run scripts/New-WorkshopAccess.ps1")
    public boolean isAccessConfigured() {
        return !enabled || (accessCodeHash != null && accessCodeHash.matches("[a-f0-9]{64}"));
    }
}
