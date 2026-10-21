package com.contoso.store.assistant;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestBudgetTest {

    @Test
    void enforcesTheExactMinuteLimitAndRecoversAfterTheWindow() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T12:00:00Z");
        when(clock.instant()).thenReturn(start);
        var budget = new RequestBudget(clock);
        for (int i = 0; i < 20; i++) budget.reserve();
        assertThatThrownBy(budget::reserve).isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("request_budget");
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(budget::reserve).doesNotThrowAnyException();
    }

    @Test
    void enforcesTheExactHourLimit() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T12:00:00Z");
        var budget = new RequestBudget(clock);
        for (int minute = 0; minute < 12; minute++) {
            when(clock.instant()).thenReturn(start.plusSeconds(minute * 61L));
            for (int i = 0; i < 20; i++) budget.reserve();
        }
        when(clock.instant()).thenReturn(start.plusSeconds(12 * 61L));
        assertThatThrownBy(budget::reserve).isInstanceOf(AssistantException.class);
        when(clock.instant()).thenReturn(start.plusSeconds(3600));
        assertThatCode(budget::reserve).doesNotThrowAnyException();
    }
}
