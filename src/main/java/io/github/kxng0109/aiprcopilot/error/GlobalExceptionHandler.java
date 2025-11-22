package io.github.kxng0109.aiprcopilot.error;

import io.github.kxng0109.aiprcopilot.config.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for managing application-specific and generic exceptions.
 * <p>
 * Provides custom error responses for exceptions while ensuring appropriate HTTP status codes.
 *
 * <p>Automatically invoked by the Spring framework for any unhandled exceptions thrown within
 * {@code @RestController} components.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@code DiffTooLargeException} by constructing an {@code ErrorResponse}
     * and returning it wrapped in a {@code ResponseEntity} with an HTTP 413 (Payload Too Large) status code.
     *
     * @param ex      the exception that occurred, must not be {@code null}
     * @param request the HTTP request that caused the exception, must not be {@code null}
     * @return a response entity containing error details, never {@code null}
     */
    @ExceptionHandler(DiffTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleDiffTooLargeException(
            DiffTooLargeException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        ErrorResponse errorResponse = ErrorResponse.builder()
                                                   .timestamp(OffsetDateTime.now())
                                                   .statusCode(status.value())
                                                   .error(status.getReasonPhrase())
                                                   .message(ex.getMessage())
                                                   .path(request.getRequestURI())
                                                   .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles {@code NoResourceFoundException} by constructing an {@code ErrorResponse} and
     * returning it wrapped in a {@code ResponseEntity} with an HTTP 404 status code.
     *
     * @param ex      the exception that occurred, must not be {@code null}
     * @param request the HTTP request that caused the exception, must not be {@code null}
     * @return a response entity containing error information, never {@code null}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse errorResponse = ErrorResponse.builder()
                                                   .timestamp(OffsetDateTime.now())
                                                   .statusCode(status.value())
                                                   .error(status.getReasonPhrase())
                                                   .message(ex.getMessage())
                                                   .path(request.getRequestURI())
                                                   .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles {@code MethodArgumentNotValidException} by constructing an {@code ErrorResponse} containing
     * validation error details and returning it in a {@code ResponseEntity} with an HTTP 400 status code.
     *
     * @param ex      the exception that occurred, must not be {@code null}
     * @param request the HTTP request that caused the exception, must not be {@code null}
     * @return a response entity containing error information, never {@code null}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                                                   .timestamp(OffsetDateTime.now())
                                                   .statusCode(status.value())
                                                   .error(status.getReasonPhrase())
                                                   .message(errors.toString())
                                                   .path(request.getRequestURI())
                                                   .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles {@code HttpRequestMethodNotSupportedException} by generating a response entity
     * containing error details and an HTTP 405 status code.
     *
     * @param ex      the exception that occurred, must not be {@code null}
     * @param request the HTTP request that caused the exception, must not be {@code null}
     * @return a response entity containing error information, never {@code null}
     */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;

        ErrorResponse errorResponse = ErrorResponse.builder()
                                                   .timestamp(OffsetDateTime.now())
                                                   .statusCode(status.value())
                                                   .error(status.getReasonPhrase())
                                                   .message(ex.getMessage())
                                                   .path(request.getRequestURI())
                                                   .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles general exceptions by constructing an {@code ErrorResponse} and returning it
     * wrapped in a {@code ResponseEntity} with an HTTP 500 status code.
     *
     * @param ex      the exception that occurred, must not be {@code null}
     * @param request the HTTP request that caused the exception, must not be {@code null}
     * @return a response entity containing error information, never {@code null}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse errorResponse = ErrorResponse.builder()
                                                   .timestamp(OffsetDateTime.now())
                                                   .statusCode(status.value())
                                                   .error(status.getReasonPhrase())
                                                   .message(ex.getMessage())
                                                   .path(request.getRequestURI())
                                                   .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}
