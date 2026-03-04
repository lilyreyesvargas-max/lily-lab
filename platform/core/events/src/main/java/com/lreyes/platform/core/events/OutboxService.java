package com.lreyes.platform.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestiona la tabla outbox: guarda eventos y procesa los pendientes.
 * <p>
 * El flujo típico es:
 * <ol>
 *   <li>Un servicio de negocio llama a {@link DomainEventPublisher#publish(DomainEvent)}</li>
 *   <li>El publisher guarda el evento aquí vía {@link #save(DomainEvent, String, UUID)}</li>
 *   <li>{@link OutboxScheduler} llama periódicamente a {@link #processPending(int)}</li>
 *   <li>Los eventos pendientes se publican al {@link ExternalEventPublisher}</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository repository;
    private final ExternalEventPublisher externalPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Guarda un evento de dominio en la tabla outbox.
     * Se ejecuta dentro de la misma transacción que la operación de negocio.
     */
    @Transactional
    public OutboxEvent save(DomainEvent event, String aggregateType, UUID aggregateId) {
        String payload = serializeEvent(event);

        OutboxEvent outbox = OutboxEvent.builder()
                .eventType(event.eventType())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .tenantId(event.getTenantId())
                .payload(payload)
                .build();

        OutboxEvent saved = repository.save(outbox);
        log.debug("Evento guardado en outbox: type='{}', aggregate='{}:{}'",
                event.eventType(), aggregateType, aggregateId);
        return saved;
    }

    /**
     * Procesa un batch de eventos pendientes.
     *
     * @param batchSize número máximo de eventos a procesar
     * @return número de eventos procesados exitosamente
     */
    @Transactional
    public int processPending(int batchSize) {
        List<OutboxEvent> pending = repository.findPendingEvents(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                String topic = "platform." + event.getAggregateType();
                String key = event.getAggregateId() != null
                        ? event.getAggregateId().toString()
                        : event.getId().toString();

                externalPublisher.publish(topic, key, event.getPayload());
                event.markPublished();
                published++;
            } catch (Exception e) {
                log.error("Error publicando evento outbox {}: {}",
                        event.getId(), e.getMessage());
                event.markFailed(e.getMessage());
            }
        }

        repository.saveAll(pending);
        if (published > 0) {
            log.info("Outbox: {} de {} eventos publicados", published, pending.size());
        }
        return published;
    }

    public long countPending() {
        return repository.countByStatus(OutboxEvent.OutboxStatus.PENDING);
    }

    private String serializeEvent(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento de dominio", e);
        }
    }
}
