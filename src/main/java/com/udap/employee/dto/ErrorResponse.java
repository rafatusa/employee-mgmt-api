package com.udap.employee.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned for every handled failure. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details) {

    public static ErrorResponse of(final int status, final String error, final String message) {
        return new ErrorResponse(Instant.now(), status, error, message, Map.of());
    }

    public static ErrorResponse of(
            final int status, final String error, final String message, final Map<String, String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
