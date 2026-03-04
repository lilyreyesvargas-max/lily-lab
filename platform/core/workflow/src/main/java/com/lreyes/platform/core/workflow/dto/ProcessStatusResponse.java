package com.lreyes.platform.core.workflow.dto;

import java.util.List;
import java.util.Map;

/**
 * Estado actual de una instancia de proceso.
 */
public record ProcessStatusResponse(
        String processInstanceId,
        String processDefinitionKey,
        String businessKey,
        String tenantId,
        boolean ended,
        Map<String, Object> variables,
        List<TaskResponse> activeTasks
) {}
