package com.lreyes.platform.core.events;

import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publisher central de eventos de dominio.
 * <p>
 * Doble publicación:
 * <ol>
 *   <li><b>Local (síncrono)</b>: vía {@link ApplicationEventPublisher} de Spring.
 *       Los listeners con {@code @EventListener} o {@code @TransactionalEventListener}
 *       reciben el evento en el mismo proceso.</li>
 *   <li><b>Outbox (persistente)</b>: guarda en tabla {@code outbox_events}.
 *       El {@link OutboxScheduler} lo publicará externamente después.</li>
 * </ol>
 * <p>
 * Uso desde un servicio de negocio:
 * <pre>
 * &#64;Service
 * public class CustomerService {
 *     private final DomainEventPublisher eventPublisher;
 *
 *     &#64;Transactional
 *     public Customer create(CreateCustomerDto dto) {
 *         Customer saved = repository.save(customer);
 *         eventPublisher.publish(
 *             new CustomerCreatedEvent(saved.getTenantId(), saved.getId(), saved.getName()),
 *             "customer",
 *             saved.getId()
 *         );
 *         return saved;
 *     }
 * }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;
    private final OutboxService outboxService;

    /**
     * Publica un evento de dominio local y lo persiste en el outbox.
     *
     * @param event         el evento de dominio
     * @param aggregateType tipo del agregado (ej: "customer", "order")
     * @param aggregateId   ID del agregado que originó el evento
     */
    public void publish(DomainEvent event, String aggregateType, UUID aggregateId) {
        // 1. Publicación local (síncrona, misma transacción)
        springPublisher.publishEvent(event);
        log.debug("Evento publicado localmente: {}", event.eventType());

        // 2. Persistir en outbox (misma transacción de negocio)
        outboxService.save(event, aggregateType, aggregateId);
    }

    /**
     * Publica solo localmente (sin outbox).
     * Útil para eventos internos que no necesitan entrega externa.
     */
    public void publishLocal(DomainEvent event) {
        springPublisher.publishEvent(event);
        log.debug("Evento publicado solo localmente: {}", event.eventType());
    }
}
