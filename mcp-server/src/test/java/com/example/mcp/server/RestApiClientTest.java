package com.example.mcp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestApiClientTest {

    private FakeRestApi fake;
    private RestApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        fake = new FakeRestApi();
        client = new RestApiClient(fake.baseUrl());
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    @Test
    void getSendsPathAndEncodedQueryAndReturnsBody() throws Exception {
        fake.respondWith(200, "[{\"id\":1}]");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("nameContains", "a b&c");
        query.put("skipped", null);

        RestApiClient.ApiResponse response = client.send("GET", "/items", query, null);

        assertEquals(200, response.status());
        assertEquals("[{\"id\":1}]", response.body());
        assertTrue(response.isSuccess());
        FakeRestApi.Request request = fake.lastRequest();
        assertEquals("GET", request.method());
        assertEquals("/api/v1/items", request.path());
        assertEquals("nameContains=a+b%26c", request.query(), "null values are skipped, values URL-encoded");
        assertNull(request.contentType(), "no Content-Type without a body");
    }

    @Test
    void postSendsJsonBodyWithContentType() throws Exception {
        fake.respondWith(201, "{\"id\":7}");

        RestApiClient.ApiResponse response = client.send("POST", "/items", null, "{\"name\":\"x\"}");

        assertEquals(201, response.status());
        FakeRestApi.Request request = fake.lastRequest();
        assertEquals("POST", request.method());
        assertEquals("{\"name\":\"x\"}", request.body());
        assertEquals("application/json", request.contentType());
        assertNull(request.query());
    }

    @Test
    void errorStatusIsReturnedNotThrown() throws Exception {
        fake.respondWith(404, "{\"detail\":\"Item 9 not found\"}");

        RestApiClient.ApiResponse response = client.send("GET", "/items/9", Map.of(), null);

        assertEquals(404, response.status());
        assertFalse(response.isSuccess());
        assertEquals("{\"detail\":\"Item 9 not found\"}", response.body());
    }

    @Test
    void trailingSlashOnBaseUrlIsIgnored() throws Exception {
        RestApiClient slashed = new RestApiClient(fake.baseUrl() + "/");

        slashed.send("GET", "/persons", null, null);

        assertEquals(fake.baseUrl(), slashed.baseUrl());
        assertEquals("/api/v1/persons", fake.lastRequest().path());
    }

    @Test
    void unreachableServerThrowsIOException() {
        String deadUrl = fake.baseUrl();
        fake.close();
        RestApiClient dead = new RestApiClient(deadUrl);

        assertThrows(IOException.class, () -> dead.send("GET", "/items", null, null));
    }
}
