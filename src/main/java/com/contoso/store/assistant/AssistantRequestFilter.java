package com.contoso.store.assistant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AssistantRequestFilter extends OncePerRequestFilter {

    private final JsonMapper mapper;

    public AssistantRequestFilter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/api/assistant/messages") || !request.getMethod().equals("POST");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0 || length > 4096) {
            HttpStatus status = length < 0 ? HttpStatus.LENGTH_REQUIRED : HttpStatus.CONTENT_TOO_LARGE;
            response.setStatus(status.value());
            response.setContentType("application/problem+json");
            mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status,
                    "Send a JSON question with Content-Length and a body no larger than 4096 bytes."));
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
