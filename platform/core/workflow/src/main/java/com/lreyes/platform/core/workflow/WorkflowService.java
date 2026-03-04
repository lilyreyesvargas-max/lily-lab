package com.lreyes.platform.core.workflow;

import com.lreyes.platform.core.workflow.dto.ProcessStatusResponse;
import com.lreyes.platform.core.workflow.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Servicio que encapsula las operaciones de Flowable.
 * <p>
 * Separa la lógica de workflow del controlador REST, facilitando
 * que los módulos de negocio invoquen procesos programáticamente
 * sin depender del controller.
 * <p>
 * NOTA sobre tenantId: Flowable soporta multi-tenancy nativo.
 * Al iniciar un proceso, se pasa el tenantId del contexto actual.
 * Las consultas de tareas filtran por tenantId para garantizar aislamiento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    /**
     * Inicia una instancia de proceso.
     *
     * @param processKey  clave de definición (ej: "venta-aprobacion")
     * @param tenantId    tenant que inicia el proceso
     * @param businessKey clave de negocio (ej: orderId)
     * @param variables   variables del proceso
     * @return ID de la instancia creada
     */
    @Transactional
    public String startProcess(String processKey, String tenantId,
                               String businessKey, Map<String, Object> variables) {
        ProcessInstance instance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(processKey)
                .tenantId(tenantId)
                .businessKey(businessKey)
                .variables(variables)
                .start();

        log.info("Proceso iniciado: key='{}', instanceId='{}', tenant='{}', businessKey='{}'",
                processKey, instance.getId(), tenantId, businessKey);
        return instance.getId();
    }

    /**
     * Lista tareas asignadas a un usuario específico.
     */
    public List<TaskResponse> getTasksByAssignee(String assignee, String tenantId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .taskTenantId(tenantId)
                .includeProcessVariables()
                .orderByTaskCreateTime().desc()
                .list();
        return tasks.stream().map(this::toTaskResponse).toList();
    }

    /**
     * Lista tareas candidatas para un grupo/rol (sin asignar aún).
     */
    public List<TaskResponse> getTasksByCandidateGroup(String group, String tenantId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateGroup(group)
                .taskTenantId(tenantId)
                .includeProcessVariables()
                .orderByTaskCreateTime().desc()
                .list();
        return tasks.stream().map(this::toTaskResponse).toList();
    }

    /**
     * Lista TODAS las tareas pendientes de un tenant (asignadas + candidatas).
     */
    public List<TaskResponse> getAllPendingTasks(String tenantId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .includeProcessVariables()
                .orderByTaskCreateTime().desc()
                .list();
        return tasks.stream().map(this::toTaskResponse).toList();
    }

    /**
     * Reclama una tarea (la asigna a un usuario).
     */
    @Transactional
    public void claimTask(String taskId, String userId) {
        taskService.claim(taskId, userId);
        log.info("Tarea reclamada: taskId='{}', userId='{}'", taskId, userId);
    }

    /**
     * Completa una tarea, avanzando el proceso.
     *
     * @param taskId    ID de la tarea
     * @param variables variables a establecer (ej: aprobado=true)
     */
    @Transactional
    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
        log.info("Tarea completada: taskId='{}', variables={}", taskId, variables.keySet());
    }

    /**
     * Obtiene el estado de una instancia de proceso (activa o histórica).
     */
    public ProcessStatusResponse getProcessStatus(String processInstanceId) {
        // Primero intentar en procesos activos
        ProcessInstance active = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();

        if (active != null) {
            List<TaskResponse> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .includeProcessVariables()
                    .list()
                    .stream().map(this::toTaskResponse).toList();

            return new ProcessStatusResponse(
                    active.getId(),
                    active.getProcessDefinitionKey(),
                    active.getBusinessKey(),
                    active.getTenantId(),
                    false,
                    active.getProcessVariables(),
                    activeTasks
            );
        }

        // Si no está activo, buscar en histórico
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();

        if (historic == null) {
            return null;
        }

        return new ProcessStatusResponse(
                historic.getId(),
                historic.getProcessDefinitionKey(),
                historic.getBusinessKey(),
                historic.getTenantId(),
                historic.getEndTime() != null,
                historic.getProcessVariables(),
                List.of()
        );
    }

    private TaskResponse toTaskResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getProcessInstanceId(),
                extractProcessDefinitionKey(task),
                task.getAssignee(),
                task.getTenantId(),
                task.getCreateTime() != null ? task.getCreateTime().toInstant() : null,
                task.getProcessVariables()
        );
    }

    private String extractProcessDefinitionKey(Task task) {
        if (task.getProcessDefinitionId() == null) return null;
        // Format: "key:version:id" → extract key
        String defId = task.getProcessDefinitionId();
        int colonIdx = defId.indexOf(':');
        return colonIdx > 0 ? defId.substring(0, colonIdx) : defId;
    }
}
