package com.contoso.store.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantOfflineTest {

    @Autowired MockMvc mvc;

    @Test
    void offlineStartupDoesNotCreateAModelClient() throws Exception {
        mvc.perform(get("/api/assistant/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/assistant/messages").contentType("application/json")
                        .content("{\"message\":\"Find a jacket\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("offline"));
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12));
    }
}
