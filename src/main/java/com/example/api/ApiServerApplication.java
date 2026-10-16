package com.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REST API server.
 *
 * <p>The API is spec-first: src/main/resources/openapi/api.yaml defines every endpoint and
 * model, and the build generates Java interfaces/models from it. Request flow:
 * <pre>
 *   HTTP request
 *     → controller (implements a generated *Api interface; validation happens here)
 *     → service    (business logic + mapping)
 *     → InMemoryStore (storage)
 * </pre>
 * Errors are turned into problem+json responses by the exception handler.
 *
 * <p>Run with {@code ./mvnw spring-boot:run}; the server listens on port 8080.
 */
@SpringBootApplication
public class ApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }
}
