package com.lreyes.platform.core.workflow;

import com.lreyes.platform.core.workflow.dto.CompleteTaskRequest;
import com.lreyes.platform.core.workflow.dto.ProcessStatusResponse;
import com.lreyes.platform.core.workflow.dto.StartProcessRequest;
import com.lreyes.platform.core.workflow.dto.TaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST para gestión de procesos Flowable.
 * <p>
 * El tenantId se extrae del header {@code X-Tenant-Id} (ya establecido
 * por el TenantFilter en los pasos anteriores).
 *
 * <pre>
 * POST   /api/workflow/processes           → Iniciar proceso
 * GET    /api/workflow/processes/{id}       → Estado del proceso
 * GET    /api/workflow/tasks/pending        → Tareas pendientes del tenant
 * GET    /api/workflow/tasks/assignee/{u}   → Tareas asignadas a usuario
 * GET    /api/workflow/tasks/group/{g}      → Tareas candidatas por grupo/rol
 * POST   /api/workflow/tasks/{id}/claim     → Reclamar tarea
 * POST   /api/workflow/tasks/{id}/complete  → Completar tarea
 * </pre>
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // ───────── Procesos ─────────

    @PostMapping("/processes")
    public ResponseEntity<Map<String, String>> startProcess(
            @Valid @RequestBody StartProcessRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {

        String processInstanceId = workflowService.startProcess(
                request.processKey(),
                tenantId,
                request.businessKey(),
                request.variables());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("processInstanceId", processInstanceId));
    }

    @GetMapping("/processes/{processInstanceId}")
    public ResponseEntity<ProcessStatusResponse> getProcessStatus(
            @PathVariable String processInstanceId) {

        ProcessStatusResponse status = workflowService.getProcessStatus(processInstanceId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    // ───────── Tareas ─────────

    @GetMapping("/tasks/pending")
    public ResponseEntity<List<TaskResponse>> getPendingTasks(
            @RequestHeader("X-Tenant-Id") String tenantId) {

        return ResponseEntity.ok(workflowService.getAllPendingTasks(tenantId));
    }

    @GetMapping("/tasks/assignee/{assignee}")
    public ResponseEntity<List<TaskResponse>> getTasksByAssignee(
            @PathVariable String assignee,
            @RequestHeader("X-Tenant-Id") String tenantId) {

        return ResponseEntity.ok(workflowService.getTasksByAssignee(assignee, tenantId));
    }

    @GetMapping("/tasks/group/{group}")
    public ResponseEntity<List<TaskResponse>> getTasksByCandidateGroup(
            @PathVariable String group,
            @RequestHeader("X-Tenant-Id") String tenantId) {

        return ResponseEntity.ok(workflowService.getTasksByCandidateGroup(group, tenantId));
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<Void> claimTask(
            @PathVariable String taskId,
            @RequestParam String userId) {

        workflowService.claimTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody(required = false) CompleteTaskRequest request) {

        Map<String, Object> vars = (request != null) ? request.variables() : Map.of();
        workflowService.completeTask(taskId, vars);
        return ResponseEntity.ok().build();
    }
}
