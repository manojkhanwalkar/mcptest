package com.example.api.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts exceptions thrown by any controller into RFC 9457 problem+json responses
 * (the {@code Problem} schema in api.yaml).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means Spring's own errors are already
 * covered: invalid request bodies (e.g. a blank name or bad email, which raise
 * {@code MethodArgumentNotValidException}), malformed JSON, unsupported media types, etc.
 * The handlers below cover the remaining cases.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** A service couldn't find the requested resource → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * A path or query parameter broke a constraint from the spec (e.g. {@code id} below
     * {@code minimum: 1}) → 400. These are raised by the {@code @Validated} annotation on the
     * generated API interfaces, which Spring's base handler doesn't map on its own.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
