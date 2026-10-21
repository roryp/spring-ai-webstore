package com.contoso.store.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkshopAccess {

    private final AssistantProperties properties;

    public WorkshopAccess(AssistantProperties properties) {
        this.properties = properties;
    }

    public void check(String code) {
        if (code == null || code.isBlank() || code.length() > 128 || !properties.enabled()) {
            throw denied();
        }
        if (!MessageDigest.isEqual(hash(code), HexFormat.of().parseHex(properties.accessCodeHash()))) {
            throw denied();
        }
    }

    static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This runtime must support SHA-256", exception);
        }
    }

    private AssistantException denied() {
        return new AssistantException(HttpStatus.UNAUTHORIZED, "workshop_access",
                "Enter the workshop access code supplied by the presenter.");
    }
}
