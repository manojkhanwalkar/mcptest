package com.example.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Builds the standard response for a successful create (POST) call, shared by all controllers.
 */
final class CreatedResponses {

    private CreatedResponses() {
    }

    /**
     * Returns {@code 201 Created} with the new resource as the body and a {@code Location}
     * header pointing at it.
     *
     * <p>The location is derived from the current request URL, so a POST to
     * {@code http://host/api/v1/items} that creates id 7 yields
     * {@code Location: http://host/api/v1/items/7}. Must be called on a request thread.
     *
     * @param id   id of the newly created resource
     * @param body the created resource, returned as the response body
     */
    static <T> ResponseEntity<T> created(long id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
