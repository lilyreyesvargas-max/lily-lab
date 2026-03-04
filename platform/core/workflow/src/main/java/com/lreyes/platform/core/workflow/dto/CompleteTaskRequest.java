package com.lreyes.platform.core.workflow.dto;

import java.util.Map;

/**
 * Request para completar una tarea.
 *
 * @param variables variables a establecer al completar (ej: aprobado=true)
 */
public record CompleteTaskRequest(
        Map<String, Object> variables
) {
    public CompleteTaskRequest {
        if (variables == null) variables = Map.of();
    }
}
