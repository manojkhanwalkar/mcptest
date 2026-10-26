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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for /api/v1/items: each request goes through the full Spring MVC stack
 * (routing, validation from api.yaml, exception handling) via MockMvc, without a real server.
 *
 * <p>The Spring context, and therefore the in-memory store, is shared across tests, so tests
 * must not assume the store is empty or that ids start at 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Full lifecycle: create → read → delete → read again returns 404. */
    @Test
    void createGetAndDeleteItem() throws Exception {
        String location = mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"widget\",\"description\":\"a test item\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("widget"))
                .andReturn().getResponse().getHeader("Location");

        // Follow the Location header rather than building the URL by hand.
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("widget"));

        mockMvc.perform(delete(location))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isNotFound());
    }

    /** name has minLength: 1 in the spec. */
    @Test
    void rejectsInvalidItem() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // The tests below exercise constraints declared only in api.yaml (no hand-written validation).

    /** price has minimum: 0. */
    @Test
    void rejectsNegativePrice() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"widget\",\"price\":-1}"))
                .andExpect(status().isBadRequest());
    }

    /** Path id has minimum: 1; handled by GlobalExceptionHandler.handleConstraintViolation. */
    @Test
    void rejectsIdBelowMinimum() throws Exception {
        mockMvc.perform(get("/api/v1/items/0"))
                .andExpect(status().isBadRequest())
                // names the parameter, not the Java method (was "getItem.id: ...")
                .andExpect(jsonPath("$.detail").value("id: must be greater than or equal to 1"));
    }

    /** nameContains is a case-insensitive substring match ("gadget" matches "Blue Gadget"). */
    @Test
    void filtersByName() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blue Gadget\",\"price\":9.99}"))
                .andExpect(status().isCreated());

        // Other tests may have created items too, but none of them contain "gadget".
        mockMvc.perform(get("/api/v1/items").param("nameContains", "gadget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Blue Gadget"))
                .andExpect(jsonPath("$[0].price").value(9.99));
    }
}
