package com.contoso.store;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final Assistant assistant;
    private final Catalog catalog;

    ChatController(Assistant assistant, Catalog catalog) {
        this.assistant = assistant;
        this.catalog = catalog;
    }

    @PostMapping("/ask")
    String ask(@RequestParam String question, Model model) {
        var answer = answer(question);
        model.addAttribute("products", catalog.all());
        model.addAttribute("question", question);
        model.addAttribute("answer", answer.text());
        model.addAttribute("toolCalls", answer.toolCalls());
        return "index";
    }

    private Answer answer(String question) {
        if (question.isBlank() || question.length() > 300) {
            return new Answer("Please ask a question of up to 300 characters.", List.of());
        }
        try {
            return assistant.answer(question);
        }
        catch (RuntimeException e) {
            log.warn("The AI model call failed", e);
            return new Answer("Sorry, the assistant could not answer right now. Please try again in a moment.", List.of());
        }
    }
}
