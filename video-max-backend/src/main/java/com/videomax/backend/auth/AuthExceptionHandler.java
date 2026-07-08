package com.videomax.backend.auth;

import com.videomax.backend.auth.exception.EmailAlreadyRegisteredException;
import com.videomax.backend.auth.exception.InvalidCredentialsException;
import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.exception.RateLimitExceededException;
import com.videomax.backend.auth.exception.WeakPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps {@code auth} domain exceptions to RFC 7807 {@link ProblemDetail}
 * responses. Lives inside the {@code auth} module so the cross-cutting
 * {@code config} module does not depend on auth-specific exception types.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final URI AUTH_ERROR_TYPE = URI.create("https://api.videomax.com/errors/auth");

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "AUTH_EMAIL_TAKEN");
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(WeakPasswordException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), "AUTH_WEAK_PASSWORD");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), "AUTH_INVALID_CREDENTIALS");
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ProblemDetail handleInvalidResetToken(InvalidResetTokenException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), "AUTH_INVALID_RESET_TOKEN");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "AUTH_RATE_LIMIT_EXCEEDED");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(AUTH_ERROR_TYPE);
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
