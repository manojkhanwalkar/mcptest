package com.example.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request bodies over {@code api.max-request-body-bytes} are rejected before they are parsed. */
@SpringBootTest
@AutoConfigureMockMvc
class RequestBodySizeLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oversizedBodyIsRejectedWith413() throws Exception {
        String body = "{\"name\":\"" + "x".repeat(70_000) + "\"}";

        mockMvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void normalBodyIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ok\"}"))
                .andExpect(status().isCreated());
    }
}
