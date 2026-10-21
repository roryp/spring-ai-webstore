package com.contoso.store.assistant;

import jakarta.validation.Validation;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkshopAccessTest {

    @Test
    void acceptsOnlyTheCorrectWorkshopCode() {
        String hash = HexFormat.of().formatHex(WorkshopAccess.hash("test-only-code"));
        var access = new WorkshopAccess(new AssistantProperties(true, hash));
        assertThatCode(() -> access.check("test-only-code")).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.check("wrong")).isInstanceOf(AssistantException.class);
        assertThatThrownBy(() -> access.check(null)).isInstanceOf(AssistantException.class);
    }

    @Test
    void enabledAiCannotStartWithoutAccessProtection() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new AssistantProperties(true, ""))).isNotEmpty();
            assertThat(validator.validate(new AssistantProperties(false, ""))).isEmpty();
        }
    }
}
