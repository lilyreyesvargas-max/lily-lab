package com.lreyes.platform.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio base. Todos los eventos de negocio deben extender este record o clase.
 * <p>
 * Contiene metadata mínima: id del evento, timestamp, tenant que lo originó.
 * Los módulos core/events se encargarán de publicar y transportar estos eventos.
 */
public abstract class DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;
    private final String tenantId;

    protected DomainEvent(String tenantId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.tenantId = tenantId;
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getTenantId() { return tenantId; }

    /**
     * Nombre del tipo de evento, ej: "customer.created", "sale.approved".
     * Usado como routing key en mensajería.
     */
    public abstract String eventType();
}
