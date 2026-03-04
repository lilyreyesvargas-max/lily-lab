package com.lreyes.platform.config;

import com.lreyes.platform.shared.domain.DomainException;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Manejo centralizado de excepciones → JSON ErrorResponse (RFC 9457).
 * <p>
 * Cada respuesta incluye:
 * <ul>
 *   <li>{@code status}: código HTTP</li>
 *   <li>{@code errorCode}: código interno para i18n / frontend</li>
 *   <li>{@code detail}: mensaje legible</li>
 *   <li>{@code instance}: requestId del MDC (trazabilidad)</li>
 *   <li>{@code timestamp}: cuándo ocurrió</li>
 *   <li>{@code errors}: errores de campo (solo en validación)</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ──

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entidad no encontrada: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    // ── 422 Domain Error ──

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        log.warn("Error de dominio [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    // ── 400 Validation ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        fe.getRejectedValue()))
                .toList();

        log.warn("Errores de validación: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .errorCode("VALIDATION_ERROR")
                        .detail("Error de validación en los datos enviados")
                        .errors(fieldErrors)
                        .instance(getRequestId())
                        .timestamp(java.time.Instant.now())
                        .build());
    }

    // ── 400 Bad Request ──

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Parámetro '%s' con valor '%s' no es de tipo %s",
                ex.getName(), ex.getValue(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "esperado");
        log.warn("Type mismatch: {}", detail);
        return buildResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", detail);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Header faltante: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Header requerido: " + ex.getHeaderName());
    }

    // ── 403 Forbidden ──

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tiene permisos para esta operación");
    }

    // ── 409 Conflict (Optimistic Locking) ──

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflicto de concurrencia: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK",
                "El registro fue modificado por otro usuario. Recargue e intente de nuevo.");
    }

    // ── 409 Conflict (Data Integrity) ──

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "Operación no permitida: violación de restricción de datos (clave duplicada o referencia inválida)");
    }

    // ── 500 Catch-all ──

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Error no manejado", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno del servidor");
    }

    // ── Helpers ──

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorCode, String detail) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .errorCode(errorCode)
                        .detail(detail)
                        .instance(getRequestId())
                        .timestamp(java.time.Instant.now())
                        .build());
    }

    private String getRequestId() {
        return MDC.get("requestId");
    }
}
