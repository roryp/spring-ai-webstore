package com.contoso.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chat;
    private final Catalog catalog;

    ChatController(ChatClient.Builder builder, InventoryTools tools, Catalog catalog) {
        this.chat = builder
                .defaultSystem("""
                        You are the Trail Store assistant. Only help with questions about the store.
                        Call listProducts to get products, prices, and stock. Never invent products.
                        Answer in plain text, in two or three short sentences.
                        """)
                .defaultTools(tools)
                .build();
        this.catalog = catalog;
    }

    @PostMapping("/ask")
    String ask(@RequestParam String question, Model model) {
        model.addAttribute("products", catalog.all());
        model.addAttribute("question", question);
        model.addAttribute("answer", answer(question));
        return "index";
    }

    private String answer(String question) {
        if (question.isBlank() || question.length() > 300) {
            return "Please ask a question of up to 300 characters.";
        }
        try {
            return chat.prompt().user(question).call().content();
        }
        catch (RuntimeException e) {
            log.warn("The AI model call failed", e);
            return "Sorry, the assistant could not answer right now. Please try again in a moment.";
        }
    }
}
