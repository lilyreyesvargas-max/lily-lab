package com.lreyes.platform.core.events;

import com.lreyes.platform.shared.domain.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher springPublisher;

    @Mock
    private OutboxService outboxService;

    private DomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DomainEventPublisher(springPublisher, outboxService);
    }

    @Test
    void publish_debePublicarLocalYGuardarEnOutbox() {
        // Arrange
        var event = new TestEvent("acme");
        UUID aggregateId = UUID.randomUUID();

        // Act
        publisher.publish(event, "customer", aggregateId);

        // Assert: publicación local
        verify(springPublisher).publishEvent(event);

        // Assert: persistencia en outbox
        verify(outboxService).save(event, "customer", aggregateId);
    }

    @Test
    void publishLocal_soloPublicaLocalSinOutbox() {
        // Arrange
        var event = new TestEvent("globex");

        // Act
        publisher.publishLocal(event);

        // Assert: publicación local
        verify(springPublisher).publishEvent(event);

        // Assert: NO guarda en outbox
        verifyNoInteractions(outboxService);
    }

    @Test
    void publish_propagaMetadataDelEvento() {
        // Arrange
        var event = new TestEvent("acme");
        UUID aggregateId = UUID.randomUUID();

        // Act
        publisher.publish(event, "order", aggregateId);

        // Assert
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxService).save(captor.capture(), eq("order"), eq(aggregateId));

        DomainEvent captured = captor.getValue();
        assertThat(captured.getTenantId()).isEqualTo("acme");
        assertThat(captured.eventType()).isEqualTo("test.created");
        assertThat(captured.getEventId()).isNotNull();
        assertThat(captured.getOccurredAt()).isNotNull();
    }

    /**
     * Evento de prueba concreto.
     */
    static class TestEvent extends DomainEvent {
        TestEvent(String tenantId) {
            super(tenantId);
        }

        @Override
        public String eventType() {
            return "test.created";
        }
    }
}
