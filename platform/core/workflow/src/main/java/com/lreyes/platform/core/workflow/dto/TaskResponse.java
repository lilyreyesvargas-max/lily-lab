package com.lreyes.platform.core.workflow.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Representación de una tarea de usuario Flowable.
 */
public record TaskResponse(
        String id,
        String name,
        String description,
        String processInstanceId,
        String processDefinitionKey,
        String assignee,
        String tenantId,
        Instant createTime,
        Map<String, Object> processVariables
) {}
