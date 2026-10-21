package com.contoso.store;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("store")
public record StoreProperties(@Min(1) @Max(4) int phase, @NotBlank String revision) {

    public String phaseName() {
        return switch (phase) {
            case 1 -> "Local";
            case 2 -> "Azure";
            case 3 -> "AI";
            case 4 -> "Observe";
            default -> throw new IllegalStateException("Invalid workshop phase: " + phase);
        };
    }
}
