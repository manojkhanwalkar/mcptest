package com.example.api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Set;
import java.util.stream.Collectors;

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
     *
     * <p>The detail names the parameter (e.g. {@code id: must be greater than or equal to 1}),
     * not the raw property path, which would expose Java method names such as {@code getItem}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        if (violations == null || violations.isEmpty()) {
            // Thrown by hand rather than by method validation: its message is our own text.
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    ex.getMessage() != null ? ex.getMessage() : "Invalid request");
        }
        String detail = violations.stream()
                .map(v -> clientPath(v.getPropertyPath()) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * The violation's path as the client knows it: parameter and property names with element
     * indexes (e.g. {@code items[2].name}). The Java method node and validator-internal nodes
     * such as {@code <list element>} or {@code <return value>} are left out.
     */
    private static String clientPath(Path path) {
        StringBuilder sb = new StringBuilder();
        for (Path.Node node : path) {
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (node.isInIterable() && !sb.isEmpty()) {
                sb.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey() != null ? node.getKey() : "").append(']');
            }
            String name = node.getName();
            if (name != null && !name.startsWith("<")) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(name);
            }
        }
        return sb.isEmpty() ? "request" : sb.toString();
    }
}
