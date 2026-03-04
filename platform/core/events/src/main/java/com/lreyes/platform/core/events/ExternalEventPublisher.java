package com.lreyes.platform.core.events;

/**
 * Puerto (interfaz) para publicar eventos a un sistema externo de mensajería.
 * <p>
 * En V1, la implementación por defecto es {@link NoOpExternalEventPublisher} (solo log).
 * En V2, se implementará con Kafka:
 * <pre>
 * &#64;Component
 * public class KafkaEventPublisher implements ExternalEventPublisher {
 *     public void publish(String topic, String key, String payload) {
 *         kafkaTemplate.send(topic, key, payload);
 *     }
 * }
 * </pre>
 * <p>
 * Al agregar la implementación Kafka como bean, Spring la inyecta automáticamente
 * y {@link NoOpExternalEventPublisher} se desactiva (por {@code @ConditionalOnMissingBean}).
 */
public interface ExternalEventPublisher {

    /**
     * Publica un evento al sistema externo.
     *
     * @param topic   tema/tópico (ej: "platform.customers", "platform.sales")
     * @param key     clave de partición (ej: aggregateId)
     * @param payload evento serializado como JSON
     */
    void publish(String topic, String key, String payload);
}
