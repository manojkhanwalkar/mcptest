package com.example.api.exception;

/**
 * Thrown when a requested resource doesn't exist. {@link GlobalExceptionHandler} turns it
 * into an HTTP 404 problem+json response, so services can throw it without knowing about HTTP.
 */
public class NotFoundException extends RuntimeException {

    /** @param message shown to the client as the problem "detail", e.g. "Item 42 not found" */
    public NotFoundException(String message) {
        super(message);
    }
}
