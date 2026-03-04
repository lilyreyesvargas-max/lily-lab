package com.lreyes.platform.core.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementación NoOp del publisher externo.
 * <p>
 * Solo registra en log. Se desactiva automáticamente cuando existe
 * otra implementación de {@link ExternalEventPublisher} (ej: Kafka en V2).
 */
@Component
@ConditionalOnMissingBean(value = ExternalEventPublisher.class, ignored = NoOpExternalEventPublisher.class)
@Slf4j
public class NoOpExternalEventPublisher implements ExternalEventPublisher {

    @Override
    public void publish(String topic, String key, String payload) {
        log.debug("Evento externo (noop): topic='{}', key='{}', payload='{}'",
                topic, key, truncate(payload, 200));
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
