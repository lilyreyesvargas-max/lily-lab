package com.lreyes.platform.core.events;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuración del módulo de eventos.
 * <p>
 * Habilita {@code @EnableScheduling} para que {@link OutboxScheduler} funcione.
 */
@Configuration
@EnableScheduling
public class EventsAutoConfiguration {
}
