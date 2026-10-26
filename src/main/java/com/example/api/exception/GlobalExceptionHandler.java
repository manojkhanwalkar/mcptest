package com.example.api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
     * <p>The detail names only the parameter (e.g. {@code id: must be greater than or equal to 1}),
     * not the full property path, which would expose Java method names such as {@code getItem}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> parameterName(v) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** The last node of the violation's path: the parameter or field name. */
    private static String parameterName(ConstraintViolation<?> violation) {
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse("value");
    }
}
