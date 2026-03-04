package com.lreyes.platform.shared.domain;

import java.util.UUID;

/**
 * Excepción lanzada cuando una entidad no se encuentra.
 * El GlobalExceptionHandler la mapea a HTTP 404 con ProblemDetails.
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String entityName, UUID id) {
        super("ENTITY_NOT_FOUND",
                String.format("%s con id '%s' no encontrado", entityName, id));
    }

    public EntityNotFoundException(String entityName, String identifier) {
        super("ENTITY_NOT_FOUND",
                String.format("%s '%s' no encontrado", entityName, identifier));
    }
}
