package com.lreyes.platform.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de error estandarizada siguiendo RFC 9457 (ProblemDetails).
 * <p>
 * Compatible con {@code application/problem+json}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String errorCode,
        Instant timestamp,
        List<FieldError> errors,
        Map<String, Object> extensions
) {

    /**
     * Error de validación de un campo específico.
     */
    public record FieldError(
            String field,
            String message,
            Object rejectedValue
    ) {}

    public static ErrorResponse of(int status, String errorCode, String detail) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .detail(detail)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(int status, String errorCode, String detail, List<FieldError> fieldErrors) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .detail(detail)
                .errors(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }
}
