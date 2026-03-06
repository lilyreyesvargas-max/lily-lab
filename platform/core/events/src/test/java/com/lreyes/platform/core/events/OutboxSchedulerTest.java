package com.lreyes.platform.core.events;

import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
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

    @Mock
    private TenantRegistryService tenantRegistryService;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(tenantRegistryService.getActiveTenantNames()).thenReturn(List.of("acme", "globex"));
        scheduler = new OutboxScheduler(outboxService, tenantRegistryService);
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
