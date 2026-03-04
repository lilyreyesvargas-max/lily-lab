package com.lreyes.platform.shared.domain;

import lombok.Getter;

/**
 * Excepción base de dominio. Los módulos crean subclases específicas.
 * <p>
 * Transporta un código de error (para i18n/ProblemDetails) y un mensaje.
 */
@Getter
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
