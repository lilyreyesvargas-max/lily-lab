package com.lreyes.platform.core.events;

import com.lreyes.platform.core.tenancy.TenantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxService outboxService;

    private TenantProperties tenantProperties;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantProperties.setTenants(List.of("acme", "globex"));
        scheduler = new OutboxScheduler(outboxService, tenantProperties);
    }

    @Test
    void processOutbox_delegaAlServicePorCadaTenant() {
        when(outboxService.processPending(anyInt())).thenReturn(3);

        scheduler.processOutbox();

        // Debe llamar una vez por cada tenant
        verify(outboxService, times(2)).processPending(50);
    }

    @Test
    void processOutbox_noFallaSiServiceLanzaExcepcion() {
        when(outboxService.processPending(anyInt()))
                .thenThrow(new RuntimeException("DB timeout"));

        // No debe lanzar excepción (catch interno por tenant)
        scheduler.processOutbox();

        verify(outboxService, times(2)).processPending(50);
    }
}
