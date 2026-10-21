package com.contoso.store.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistantController {

    private final ObjectProvider<AssistantService> service;
    private final WorkshopAccess access;
    private final RequestBudget budget;

    public AssistantController(ObjectProvider<AssistantService> service, WorkshopAccess access, RequestBudget budget) {
        this.service = service;
        this.access = access;
        this.budget = budget;
    }

    @GetMapping("/api/assistant/status")
    public Status status() {
        return new Status(service.getIfAvailable() != null);
    }

    @PostMapping("/api/assistant/messages")
    public Reply message(@Valid @RequestBody Question question,
                         @RequestHeader(name = "X-Workshop-Code", required = false) String code) {
        var assistant = service.getIfAvailable();
        if (assistant == null) {
            throw new AssistantException(HttpStatus.SERVICE_UNAVAILABLE, "offline",
                    "AI is disabled in this checkpoint. You can still browse the catalog.");
        }
        access.check(code);
        budget.reserve();
        return new Reply(assistant.answer(question.message().strip()), UUID.randomUUID().toString());
    }

    @ExceptionHandler(AssistantException.class)
    ResponseEntity<ProblemDetail> failure(AssistantException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Assistant request could not be completed");
        problem.setProperty("code", exception.code());
        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) {
            headers.set(HttpHeaders.RETRY_AFTER, "60");
        }
        return new ResponseEntity<>(problem, headers, exception.status());
    }

    public record Question(@NotBlank @Size(max = 600) String message) { }
    public record Reply(String reply, String requestId) { }
    public record Status(boolean enabled) { }
}
