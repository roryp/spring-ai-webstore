package com.contoso.store.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestBudget {

    private final Clock clock;
    private final ArrayDeque<Instant> requests = new ArrayDeque<>();

    public RequestBudget() {
        this(Clock.systemUTC());
    }

    RequestBudget(Clock clock) {
        this.clock = clock;
    }

    public synchronized void reserve() {
        Instant now = clock.instant();
        Instant hourAgo = now.minus(Duration.ofHours(1));
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(hourAgo)) {
            requests.removeFirst();
        }
        long lastMinute = requests.stream()
                .filter(time -> time.isAfter(now.minusSeconds(60))).count();
        if (requests.size() >= 240 || lastMinute >= 20) {
            throw new AssistantException(HttpStatus.TOO_MANY_REQUESTS, "request_budget",
                    "The shared workshop limit is reached. Please try again later; the catalog is still available.");
        }
        requests.addLast(now);
    }
}
