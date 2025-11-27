package io.github.kxng0109.aiprcopilot.api.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Represents an error response from a service.
 *
 * @param timestamp the timestamp when the error occurred, must not be null
 * @param statusCode the HTTP status code associated with the error, must not be negative
 * @param error a brief description of the type of error that occurred, must not be blank
 * @param message a detailed message describing what went wrong, may be null
 * @param path the URL path where the error occurred, may be null
 * @param requestId a unique identifier for the request that led to the error, may be null
 */
@Builder
public record ErrorResponse(
        OffsetDateTime timestamp,
        int statusCode,
        String error,
        String message,
        String path,
        String requestId
) {
}
