package com.lreyes.platform.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator que reporta los módulos de negocio activos.
 * <p>
 * Verifica que los servicios principales de cada módulo estén presentes
 * como beans en el ApplicationContext.
 */
@Component
public class ModulesHealthIndicator implements HealthIndicator {

    private static final Map<String, String> MODULE_BEANS = Map.of(
            "customers", "customerService",
            "employees", "employeeService",
            "sales", "salesService",
            "documents", "documentService",
            "workflow", "workflowService"
    );

    private final ApplicationContext context;

    public ModulesHealthIndicator(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Health health() {
        Map<String, String> details = new LinkedHashMap<>();
        boolean allUp = true;

        for (var entry : MODULE_BEANS.entrySet()) {
            boolean present = context.containsBean(entry.getValue());
            details.put(entry.getKey(), present ? "UP" : "NOT_LOADED");
            if (!present) allUp = false;
        }

        Health.Builder builder = allUp ? Health.up() : Health.down();
        details.forEach(builder::withDetail);
        return builder.build();
    }
}
