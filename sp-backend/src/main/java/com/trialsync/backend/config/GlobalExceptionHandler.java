package com.trialsync.backend.config;

import com.trialsync.backend.dto.common.ErrorResponse;
import com.trialsync.backend.middleware.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Port of {@code install_error_handlers}. Every failure leaves the service as the same envelope the
 * React client already parses, and no stack trace is ever serialised.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationError.class)
    public ResponseEntity<ErrorResponse> handleApplicationError(ApplicationError error) {
        return envelope(
                error.getStatusCode(),
                error.getCode(),
                error.getMessage(),
                error.getField(),
                error.getDetails());
    }

    /**
     * Body validation failures. The Python handler inspected the raw Pydantic error list and
     * remapped a few well-known cases to dedicated codes before falling back to the generic one;
     * the order of those checks is significant and preserved here.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("loc", List.of("body", fieldError.getField()));
            item.put("msg", fieldError.getDefaultMessage());
            item.put("type", fieldError.getCode() == null ? "value_error" : fieldError.getCode());
            details.add(item);
        }
        for (org.springframework.validation.ObjectError globalError :
                exception.getBindingResult().getGlobalErrors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("loc", List.of("body"));
            item.put("msg", globalError.getDefaultMessage());
            item.put("type", globalError.getCode() == null ? "value_error" : globalError.getCode());
            details.add(item);
        }
        return routeValidation(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<Map<String, Object>> details = new ArrayList<>();
        exception
                .getAllValidationResults()
                .forEach(
                        result ->
                                result.getResolvableErrors()
                                        .forEach(
                                                error -> {
                                                    Map<String, Object> item = new LinkedHashMap<>();
                                                    item.put(
                                                            "loc",
                                                            List.of(
                                                                    "query",
                                                                    result.getMethodParameter()
                                                                            .getParameterName()));
                                                    item.put("msg", error.getDefaultMessage());
                                                    item.put("type", "value_error");
                                                    details.add(item);
                                                }));
        return routeValidation(details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("loc", List.of("body"));
        item.put("msg", "The request body could not be parsed.");
        item.put("type", "value_error.jsondecode");
        List<Map<String, Object>> details = List.of(item);
        // A malformed enum such as an unexpected biological sex arrives here rather than as a
        // binding failure, so the same remapping has to apply.
        String body = exception.getMostSpecificCause().getMessage();
        if (body != null && body.contains("\"sex\"")) {
            return envelope(
                    422,
                    "PATIENT_SEX_INVALID",
                    "Biological sex must be male, female, or null.",
                    "sex",
                    details);
        }
        return routeValidation(details, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("loc", List.of("query", exception.getParameterName()));
        item.put("msg", "Field required");
        item.put("type", "missing");
        return routeValidation(List.of(item), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("loc", List.of("path", exception.getName()));
        item.put("msg", "Input should be a valid value");
        item.put("type", "value_error");
        return routeValidation(List.of(item), request);
    }

    /** Unknown routes return the same shape the Starlette 404 handler produced. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound() {
        return envelope(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.", null, null);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        return envelope(405, "HTTP_ERROR", exception.getMessage(), null, null);
    }

    /**
     * Concurrent modification. The services normally detect a stale {@code expected_updated_at} and
     * raise {@link ApplicationError} first; this is the backstop for a genuine row-version clash.
     */
    @ExceptionHandler({
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<ErrorResponse> handleOptimisticLock() {
        return envelope(
                409,
                "RESOURCE_VERSION_CONFLICT",
                "The record changed since it was loaded. Reload and try again.",
                null,
                null);
    }

    /**
     * Last resort. The message is fixed and the exception is logged rather than serialised so no
     * stack trace or SQL fragment can reach a client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return envelope(
                500,
                "INTERNAL_SERVER_ERROR",
                "The request could not be completed.",
                null,
                null);
    }

    private ResponseEntity<ErrorResponse> routeValidation(
            List<Map<String, Object>> details, HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Map<String, Object> item : details) {
            Object location = item.get("loc");
            String type = String.valueOf(item.get("type"));
            if (location instanceof List<?> parts
                    && !parts.isEmpty()
                    && "sex".equals(String.valueOf(parts.get(parts.size() - 1)))
                    && "enum".equals(type)) {
                return envelope(
                        422,
                        "PATIENT_SEX_INVALID",
                        "Biological sex must be male, female, or null.",
                        "sex",
                        details);
            }
            if ("patient_date_of_birth_in_future".equals(type)) {
                return envelope(
                        422,
                        "PATIENT_DOB_IN_FUTURE",
                        "Date of birth cannot be in the future.",
                        "date_of_birth",
                        details);
            }
        }
        if ("DELETE".equalsIgnoreCase(method) && path.contains("/facts/")) {
            return envelope(
                    422,
                    "PATIENT_FACT_REMOVAL_REASON_REQUIRED",
                    "A reason is required before removing a clinical detail.",
                    "reason",
                    details);
        }
        if (path.contains("/facts")) {
            return envelope(
                    422,
                    "PATIENT_FACT_VALUE_INVALID",
                    "The clinical detail value could not be validated.",
                    "value",
                    details);
        }
        return envelope(
                422, "REQUEST_VALIDATION_ERROR", "The request could not be validated.", null, details);
    }

    private ResponseEntity<ErrorResponse> envelope(
            int status,
            String code,
            String message,
            String field,
            List<Map<String, Object>> details) {
        ErrorResponse body =
                new ErrorResponse(
                        new ErrorResponse.ErrorDetail(
                                code, message, TraceIdContext.current(), field, details));
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }
}
