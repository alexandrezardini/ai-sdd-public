package com.videomax.backend.config;

import com.videomax.backend.auth.exception.EmailAlreadyRegisteredException;
import com.videomax.backend.auth.exception.InvalidCredentialsException;
import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.exception.RateLimitExceededException;
import com.videomax.backend.auth.exception.WeakPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI AUTH_ERROR_TYPE = URI.create("https://api.videomax.com/errors/auth");

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_EMAIL_TAKEN");
        return detail;
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(
            WeakPasswordException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_WEAK_PASSWORD");
        return detail;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(
            InvalidCredentialsException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            ex.getMessage()
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_INVALID_CREDENTIALS");
        return detail;
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ProblemDetail handleInvalidResetToken(
            InvalidResetTokenException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_INVALID_RESET_TOKEN");
        return detail;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(
            RateLimitExceededException ex,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            ex.getMessage()
        );
        detail.setType(AUTH_ERROR_TYPE);
        detail.setProperty("code", "AUTH_RATE_LIMIT_EXCEEDED");
        return detail;
    }

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
