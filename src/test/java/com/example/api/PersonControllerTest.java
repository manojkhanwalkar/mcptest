package com.example.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for /api/v1/persons: each request goes through the full Spring MVC stack
 * (routing, validation from api.yaml, exception handling) via MockMvc, without a real server.
 *
 * <p>The Spring context, and therefore the in-memory store, is shared across tests, so tests
 * must not assume the store is empty or that ids start at 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Full lifecycle: create → read → replace → delete → read again returns 404. */
    @Test
    void createGetUpdateAndDeletePerson() throws Exception {
        String location = mockMvc.perform(post("/api/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Lovelace","address":"12 St James's Square, London",
                                 "email":"ada@example.com","phoneNumber":"+44 20 7946 0958"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.address").value("12 St James's Square, London"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+44 20 7946 0958"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        // PUT replaces the whole resource: fields left out of the body (phoneNumber) are cleared.
        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada King\",\"email\":\"ada.king@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada King"))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());

        mockMvc.perform(delete(location))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isNotFound());
    }

    // The tests below exercise constraints declared only in api.yaml (no hand-written validation).

    /** email is in the spec's required list. */
    @Test
    void rejectsMissingEmail() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isBadRequest());
    }

    /** email has format: email. */
    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    /** phoneNumber must match the spec's pattern (digits plus + ( ) . - and spaces). */
    @Test
    void rejectsInvalidPhoneNumber() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"phoneNumber\":\"call me\"}"))
                .andExpect(status().isBadRequest());
    }
}
