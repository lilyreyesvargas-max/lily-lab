package com.lreyes.platform.config;

import com.lreyes.platform.shared.domain.DomainException;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("EntityNotFoundException → 404")
    void entityNotFound_returns404() {
        var ex = new EntityNotFoundException("Customer", UUID.randomUUID());
        ResponseEntity<ErrorResponse> resp = handler.handleNotFound(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo(404);
        assertThat(resp.getBody().errorCode()).isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    @DisplayName("DomainException → 422")
    void domainException_returns422() {
        var ex = new DomainException("BUSINESS_RULE", "Monto excede el límite");
        ResponseEntity<ErrorResponse> resp = handler.handleDomain(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().errorCode()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400")
    void illegalArgument_returns400() {
        var ex = new IllegalArgumentException("ID no válido");
        ResponseEntity<ErrorResponse> resp = handler.handleIllegalArgument(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("AccessDeniedException → 403")
    void accessDenied_returns403() {
        var ex = new AccessDeniedException("No autorizado");
        ResponseEntity<ErrorResponse> resp = handler.handleAccessDenied(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("OptimisticLockException → 409")
    void optimisticLock_returns409() {
        var ex = new ObjectOptimisticLockingFailureException("Customer", UUID.randomUUID());
        ResponseEntity<ErrorResponse> resp = handler.handleOptimisticLock(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("OPTIMISTIC_LOCK");
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409")
    void dataIntegrity_returns409() {
        var ex = new DataIntegrityViolationException("unique constraint violated");
        ResponseEntity<ErrorResponse> resp = handler.handleDataIntegrity(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("DATA_INTEGRITY");
    }

    @Test
    @DisplayName("Exception genérica → 500")
    void genericException_returns500() {
        var ex = new RuntimeException("algo falló");
        ResponseEntity<ErrorResponse> resp = handler.handleGeneric(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Respuesta incluye timestamp")
    void response_includesTimestamp() {
        var ex = new IllegalArgumentException("test");
        ResponseEntity<ErrorResponse> resp = handler.handleIllegalArgument(ex);

        assertThat(resp.getBody().timestamp()).isNotNull();
    }
}
