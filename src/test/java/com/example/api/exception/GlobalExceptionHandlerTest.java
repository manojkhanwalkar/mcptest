package com.example.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** ConstraintViolationExceptions thrown by hand, without violations, still map to a useful 400. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void nullViolationsKeepTheMessage() {
        var problem = handler.handleConstraintViolation(new ConstraintViolationException("name already taken", null));
        assertEquals(400, problem.getStatus());
        assertEquals("name already taken", problem.getDetail());
    }

    @Test
    void emptyViolationsWithoutMessageGetAGenericDetail() {
        var problem = handler.handleConstraintViolation(new ConstraintViolationException(null, Set.of()));
        assertEquals("Invalid request", problem.getDetail());
    }
}
