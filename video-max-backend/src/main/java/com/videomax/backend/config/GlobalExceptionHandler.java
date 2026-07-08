package com.videomax.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

/**
 * Cross-cutting handler for framework-level exceptions (e.g. Bean Validation
 * failures). Domain-specific exceptions are mapped by each module's own advice
 * so this module stays free of dependencies on feature modules.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI AUTH_ERROR_TYPE = URI.create("https://api.videomax.com/errors/auth");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_VALIDATION_FAILED");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList()
        );
        return detail;
    }
}
