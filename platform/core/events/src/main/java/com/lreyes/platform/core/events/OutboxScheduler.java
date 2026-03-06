package com.lreyes.platform.core.events;

import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que procesa eventos pendientes en la tabla outbox.
 * <p>
 * Itera por cada tenant activo en BD (no desde YAML), establece el contexto
 * y procesa un batch de eventos PENDING. Usar BD garantiza que tenants
 * creados en runtime también sean procesados.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private static final int BATCH_SIZE = 50;

    private final OutboxService outboxService;
    private final TenantRegistryService tenantRegistryService;

    @Scheduled(fixedDelayString = "${app.events.outbox-poll-ms:10000}")
    public void processOutbox() {
        for (String tenant : tenantRegistryService.getActiveTenantNames()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                int processed = outboxService.processPending(BATCH_SIZE);
                if (processed > 0) {
                    log.info("Outbox scheduler [{}]: {} eventos procesados", tenant, processed);
                }
            } catch (Exception e) {
                log.error("Error en outbox scheduler [{}]: {}", tenant, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
