package io.github.kxng0109.aiprcopilot.error;

import io.github.kxng0109.aiprcopilot.config.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * A class for handling global exceptions in a Spring Boot application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles exceptions when the diff content exceeds the maximum allowed size.
     *
     * @param ex       the exception that occurred, must not be null
     * @param request  the HTTP request that caused the exception, must not be null
     * @return a response entity containing error information, never {@code null}
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
     * Handles exceptions when a resource is not found.
     *
     * @param ex      the exception that occurred, must not be null
     * @param request the HTTP request that caused the exception, must not be null
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
     * Handles exceptions when method arguments are not valid.
     *
     * @param ex       the exception that occurred, must not be null
     * @param request  the HTTP request that caused the exception, must not be null
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
     * Handles exceptions related to invalid method arguments.
     *
     * @param ex       the exception that occurred, must not be null
     * @param request  the HTTP request that caused the exception, must not be null
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
