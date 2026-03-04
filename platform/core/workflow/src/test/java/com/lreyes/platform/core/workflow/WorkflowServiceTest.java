package com.lreyes.platform.core.workflow;

import com.lreyes.platform.core.workflow.dto.ProcessStatusResponse;
import com.lreyes.platform.core.workflow.dto.TaskResponse;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private HistoryService historyService;

    private WorkflowService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowService(runtimeService, taskService, historyService);
    }

    // ───────── startProcess ─────────

    @Test
    void startProcess_debeCrearInstanciaConTenantYVariables() {
        // Arrange
        ProcessInstanceBuilder builder = mock(ProcessInstanceBuilder.class);
        ProcessInstance instance = mock(ProcessInstance.class);

        when(runtimeService.createProcessInstanceBuilder()).thenReturn(builder);
        when(builder.processDefinitionKey("venta-aprobacion")).thenReturn(builder);
        when(builder.tenantId("acme")).thenReturn(builder);
        when(builder.businessKey("ORD-001")).thenReturn(builder);
        when(builder.variables(anyMap())).thenReturn(builder);
        when(builder.start()).thenReturn(instance);
        when(instance.getId()).thenReturn("proc-123");

        Map<String, Object> vars = Map.of("monto", 15000.0, "cliente", "Acme Corp");

        // Act
        String processId = service.startProcess("venta-aprobacion", "acme", "ORD-001", vars);

        // Assert
        assertThat(processId).isEqualTo("proc-123");
        verify(builder).processDefinitionKey("venta-aprobacion");
        verify(builder).tenantId("acme");
        verify(builder).businessKey("ORD-001");
        verify(builder).variables(vars);
    }

    // ───────── getTasksByAssignee ─────────

    @Test
    void getTasksByAssignee_debeRetornarTareasFiltradas() {
        TaskQuery query = mockTaskQuery();
        Task task = mockTask("task-1", "Revisar Venta", "user1", "acme");

        when(query.taskAssignee("user1")).thenReturn(query);
        when(query.taskTenantId("acme")).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.orderByTaskCreateTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        List<TaskResponse> result = service.getTasksByAssignee("user1", "acme");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Revisar Venta");
        assertThat(result.get(0).assignee()).isEqualTo("user1");
    }

    // ───────── getTasksByCandidateGroup ─────────

    @Test
    void getTasksByCandidateGroup_debeRetornarTareasDelGrupo() {
        TaskQuery query = mockTaskQuery();
        Task task = mockTask("task-2", "Aprobación Gerencia", null, "acme");

        when(query.taskCandidateGroup("admin")).thenReturn(query);
        when(query.taskTenantId("acme")).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.orderByTaskCreateTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        List<TaskResponse> result = service.getTasksByCandidateGroup("admin", "acme");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Aprobación Gerencia");
    }

    // ───────── claimTask ─────────

    @Test
    void claimTask_debeDelegarAlTaskService() {
        service.claimTask("task-1", "user1");
        verify(taskService).claim("task-1", "user1");
    }

    // ───────── completeTask ─────────

    @Test
    void completeTask_debeDelegarConVariables() {
        Map<String, Object> vars = Map.of("aprobado", true);
        service.completeTask("task-1", vars);
        verify(taskService).complete("task-1", vars);
    }

    // ───────── getProcessStatus: activo ─────────

    @Test
    void getProcessStatus_procesoActivo_retornaConTareas() {
        // Arrange: proceso activo
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processInstanceId("proc-1")).thenReturn(piQuery);
        when(piQuery.includeProcessVariables()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(pi);

        when(pi.getId()).thenReturn("proc-1");
        when(pi.getProcessDefinitionKey()).thenReturn("venta-aprobacion");
        when(pi.getBusinessKey()).thenReturn("ORD-001");
        when(pi.getTenantId()).thenReturn("acme");
        when(pi.getProcessVariables()).thenReturn(Map.of("monto", 5000.0));

        // Tareas activas
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc-1")).thenReturn(tq);
        when(tq.includeProcessVariables()).thenReturn(tq);
        Task task = mockTask("task-99", "Revisar Venta", null, "acme");
        when(tq.list()).thenReturn(List.of(task));

        // Act
        ProcessStatusResponse status = service.getProcessStatus("proc-1");

        // Assert
        assertThat(status).isNotNull();
        assertThat(status.ended()).isFalse();
        assertThat(status.processDefinitionKey()).isEqualTo("venta-aprobacion");
        assertThat(status.activeTasks()).hasSize(1);
    }

    // ───────── getProcessStatus: finalizado ─────────

    @Test
    void getProcessStatus_procesoFinalizado_retornaEndedTrue() {
        // Arrange: no activo
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processInstanceId("proc-2")).thenReturn(piQuery);
        when(piQuery.includeProcessVariables()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        // Histórico
        HistoricProcessInstanceQuery hQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hQuery);
        when(hQuery.processInstanceId("proc-2")).thenReturn(hQuery);
        when(hQuery.includeProcessVariables()).thenReturn(hQuery);
        when(hQuery.singleResult()).thenReturn(hpi);

        when(hpi.getId()).thenReturn("proc-2");
        when(hpi.getProcessDefinitionKey()).thenReturn("venta-aprobacion");
        when(hpi.getBusinessKey()).thenReturn("ORD-002");
        when(hpi.getTenantId()).thenReturn("globex");
        when(hpi.getEndTime()).thenReturn(new Date());
        when(hpi.getProcessVariables()).thenReturn(Map.of("aprobado", true));

        // Act
        ProcessStatusResponse status = service.getProcessStatus("proc-2");

        // Assert
        assertThat(status).isNotNull();
        assertThat(status.ended()).isTrue();
        assertThat(status.activeTasks()).isEmpty();
    }

    // ───────── getProcessStatus: no existe ─────────

    @Test
    void getProcessStatus_noExiste_retornaNull() {
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processInstanceId("proc-X")).thenReturn(piQuery);
        when(piQuery.includeProcessVariables()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery hQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hQuery);
        when(hQuery.processInstanceId("proc-X")).thenReturn(hQuery);
        when(hQuery.includeProcessVariables()).thenReturn(hQuery);
        when(hQuery.singleResult()).thenReturn(null);

        assertThat(service.getProcessStatus("proc-X")).isNull();
    }

    // ───────── Helpers ─────────

    private TaskQuery mockTaskQuery() {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        return query;
    }

    private Task mockTask(String id, String name, String assignee, String tenantId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getName()).thenReturn(name);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getTenantId()).thenReturn(tenantId);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(task.getProcessDefinitionId()).thenReturn("venta-aprobacion:1:deploy-1");
        when(task.getCreateTime()).thenReturn(new Date());
        when(task.getProcessVariables()).thenReturn(Map.of());
        return task;
    }
}
