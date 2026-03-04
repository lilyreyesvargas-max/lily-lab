package com.lreyes.platform.core.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request para iniciar un proceso BPMN.
 *
 * @param processKey  clave del proceso definido en el BPMN (ej: "venta-aprobacion")
 * @param businessKey clave de negocio opcional (ej: orderId)
 * @param variables   variables del proceso (ej: monto, cliente, vendedor)
 */
public record StartProcessRequest(
        @NotBlank String processKey,
        String businessKey,
        Map<String, Object> variables
) {
    public StartProcessRequest {
        if (variables == null) variables = Map.of();
    }
}
