package com.lreyes.platform.core.events;

import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que procesa eventos pendientes en la tabla outbox.
 * <p>
 * Itera por cada tenant registrado, establece el contexto y procesa
 * un batch de eventos PENDING.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private static final int BATCH_SIZE = 50;

    private final OutboxService outboxService;
    private final TenantProperties tenantProperties;

    @Scheduled(fixedDelayString = "${app.events.outbox-poll-ms:10000}")
    public void processOutbox() {
        for (String tenant : tenantProperties.getTenants()) {
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
