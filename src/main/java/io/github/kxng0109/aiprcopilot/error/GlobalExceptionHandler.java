package io.github.kxng0109.aiprcopilot.error;

import io.github.kxng0109.aiprcopilot.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
	 * Handles {@code DiffTooLargeException} with HTTP 413 (Payload Too Large).
	 *
	 * @param ex      the exception that occurred, must not be {@code null}
	 * @param request the HTTP request that caused the exception, must not be {@code null}
	 * @return a response entity containing error details, never {@code null}
	 */
	@ExceptionHandler(DiffTooLargeException.class)
	public ResponseEntity<ErrorResponse> handleDiffTooLargeException(DiffTooLargeException ex,
	                                                                 HttpServletRequest request
	) {
		return build(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request);
	}

	/**
	 * Handles {@code BlockedDiffException} with HTTP 400 (no LLM call was made).
	 *
	 * @param ex      the exception that occurred, must not be {@code null}
	 * @param request the HTTP request that caused the exception, must not be {@code null}
	 * @return a response entity containing error details, never {@code null}
	 */
	@ExceptionHandler(BlockedDiffException.class)
	public ResponseEntity<ErrorResponse> handleBlockedDiffException(
			BlockedDiffException ex,
			HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	/**
	 * Handles {@code NoResourceFoundException} with HTTP 404.
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
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	/**
	 * Handles {@code MethodArgumentNotValidException} with HTTP 400, including validation error details.
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
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors().forEach((FieldError error) ->
				                                               errors.put(error.getField(), error.getDefaultMessage()));

		return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
	}

	/**
	 * Handles {@code HttpRequestMethodNotSupportedException} with HTTP 405.
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
		return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
	}

	/**
	 * Handles {@code HttpMessageNotReadableException} with HTTP 400.
	 *
	 * <p>Provides a more descriptive error message if the request body is missing.
	 *
	 * @param ex      the exception that occurred, must not be {@code null}
	 * @param request the HTTP request that caused the exception, must not be {@code null}
	 * @return a response entity containing error details, never {@code null}
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
			HttpMessageNotReadableException ex,
			HttpServletRequest request
	) {
		String message = ex.getMessage();

		if (message != null && message.contains("request body is missing")) {
			message = "Request body is missing. JSON object required.";
		}

		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	/**
	 * Handles {@code ModelOutputParseException} with HTTP 422 (Unprocessable Entity).
	 *
	 * @param ex      the exception that occurred, must not be {@code null}
	 * @param request the HTTP request that caused the exception, must not be {@code null}
	 * @return a response entity containing error details, never {@code null}
	 */
	@ExceptionHandler(ModelOutputParseException.class)
	public ResponseEntity<ErrorResponse> handleModelOutputParseException(
			ModelOutputParseException ex,
			HttpServletRequest request
	) {
		return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
	}

	/**
	 * Handles {@link CustomApiException} with its corresponding HTTP status code.
	 *
	 * @param ex      the exception that occurred, must not be {@code null}
	 * @param request the HTTP request that caused the exception, must not be {@code null}
	 * @return a response entity containing error details, never {@code null}
	 */
	@ExceptionHandler(CustomApiException.class)
	public ResponseEntity<ErrorResponse> handleCustomApiException(
			CustomApiException ex,
			HttpServletRequest request
	) {
		return build(ex.getHttpStatus(), ex.getMessage(), request);
	}

	/**
	 * Handles general exceptions with HTTP 500.
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
		return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
	}

	private ResponseEntity<ErrorResponse> build(
			HttpStatus status, String message, HttpServletRequest request) {
		return build(status, message, null, request);
	}

	private ResponseEntity<ErrorResponse> build(
			HttpStatus status, String message, Map<String, String> validationErrors,
			HttpServletRequest request) {
		HttpStatus effective = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
		String effectiveMessage = message == null || message.isBlank()
				? effective.getReasonPhrase()
				: message;
		ErrorResponse errorResponse = ErrorResponse.builder()
		                                           .timestamp(OffsetDateTime.now())
		                                           .statusCode(effective.value())
		                                           .error(effective.getReasonPhrase())
		                                           .message(effectiveMessage)
		                                           .path(request.getRequestURI())
		                                           .requestId(requestIdFrom(request))
		                                           .validationErrors(validationErrors)
		                                           .build();

		return ResponseEntity.status(effective).body(errorResponse);
	}

	private static String requestIdFrom(HttpServletRequest request) {
		String header = request.getHeader(ErrorResponse.REQUEST_ID_HEADER);
		if (header != null && header.matches(ErrorResponse.REQUEST_ID_PATTERN)) {
			return header;
		}
		return null;
	}
}
