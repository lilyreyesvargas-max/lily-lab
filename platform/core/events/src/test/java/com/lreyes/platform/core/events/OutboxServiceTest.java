package com.lreyes.platform.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lreyes.platform.shared.domain.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private ExternalEventPublisher externalPublisher;

    private ObjectMapper objectMapper;
    private OutboxService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new OutboxService(repository, externalPublisher, objectMapper);
    }

    @Test
    void save_debePersistirEventoConEstadoPending() {
        // Arrange
        var event = new SampleEvent("acme", "Cliente A");
        UUID aggregateId = UUID.randomUUID();

        when(repository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        OutboxEvent saved = service.save(event, "customer", aggregateId);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo("customer.created");
        assertThat(captured.getAggregateType()).isEqualTo("customer");
        assertThat(captured.getAggregateId()).isEqualTo(aggregateId);
        assertThat(captured.getTenantId()).isEqualTo("acme");
        assertThat(captured.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(captured.getPayload()).contains("Cliente A");
    }

    @Test
    void processPending_sinEventos_retornaCero() {
        when(repository.findPendingEvents(isA(Pageable.class)))
                .thenReturn(Collections.emptyList());

        int processed = service.processPending(50);

        assertThat(processed).isZero();
        verifyNoInteractions(externalPublisher);
    }

    @Test
    void processPending_publicaYMarcaComoPublished() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("customer.created")
                .aggregateType("customer")
                .aggregateId(UUID.randomUUID())
                .tenantId("acme")
                .payload("{\"name\":\"test\"}")
                .build();

        when(repository.findPendingEvents(isA(Pageable.class)))
                .thenReturn(List.of(event));

        // Act
        int processed = service.processPending(50);

        // Assert
        assertThat(processed).isEqualTo(1);
        verify(externalPublisher).publish(
                eq("platform.customer"),
                eq(event.getAggregateId().toString()),
                eq(event.getPayload()));
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).saveAll(anyList());
    }

    @Test
    void processPending_errorExternoMarcaFailed() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("order.created")
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .tenantId("globex")
                .payload("{}")
                .build();

        when(repository.findPendingEvents(isA(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Kafka down"))
                .when(externalPublisher).publish(any(), any(), any());

        // Act
        int processed = service.processPending(50);

        // Assert
        assertThat(processed).isZero();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Kafka down");
        // Todavía PENDING (no FAILED hasta 5 reintentos)
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
    }

    @Test
    void processPending_despues5ReintentosEstadoFailed() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("order.created")
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .tenantId("globex")
                .payload("{}")
                .retryCount(4) // ya tiene 4, el próximo será el 5to
                .build();

        when(repository.findPendingEvents(isA(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Kafka down"))
                .when(externalPublisher).publish(any(), any(), any());

        // Act
        service.processPending(50);

        // Assert: 5to reintento → FAILED
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
    }

    /**
     * Evento de ejemplo para pruebas.
     */
    static class SampleEvent extends DomainEvent {
        private final String name;

        SampleEvent(String tenantId, String name) {
            super(tenantId);
            this.name = name;
        }

        public String getName() { return name; }

        @Override
        public String eventType() {
            return "customer.created";
        }
    }
}
