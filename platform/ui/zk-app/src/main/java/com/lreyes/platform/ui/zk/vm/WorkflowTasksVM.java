package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.workflow.WorkflowService;
import com.lreyes.platform.core.workflow.dto.TaskResponse;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel para la vista de procesos y tareas Flowable.
 * <p>
 * Consume {@link WorkflowService} directamente (monolito).
 * Muestra:
 * <ul>
 *   <li>Tareas pendientes del tenant actual</li>
 *   <li>Acciones: reclamar, completar</li>
 *   <li>Iniciar nuevo proceso de aprobación de venta</li>
 * </ul>
 */
public class WorkflowTasksVM {

    private WorkflowService workflowService;
    private UiUser user;

    private List<TaskResponse> pendingTasks = List.of();
    private String statusMessage;

    // Formulario para iniciar proceso
    private String orderId;
    private String cliente;
    private String vendedor;
    private Double monto;
    private String descripcion;
    private boolean showStartForm;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        try {
            workflowService = SpringUtil.getApplicationContext().getBean(WorkflowService.class);
            refreshTasks();
        } catch (Exception e) {
            statusMessage = "Servicio de workflow no disponible: " + e.getMessage();
        }
    }

    @Command
    @NotifyChange({"pendingTasks", "statusMessage"})
    public void refreshTasks() {
        if (workflowService == null || user == null) return;
        try {
            pendingTasks = workflowService.getAllPendingTasks(user.getTenantId());
            statusMessage = pendingTasks.isEmpty()
                    ? "No hay tareas pendientes"
                    : pendingTasks.size() + " tarea(s) pendiente(s)";
        } catch (Exception e) {
            statusMessage = "Error al consultar tareas: " + e.getMessage();
            pendingTasks = List.of();
        }
    }

    @Command
    @NotifyChange({"pendingTasks", "statusMessage"})
    public void claimTask(@BindingParam("taskId") String taskId) {
        if (workflowService == null || user == null) return;
        try {
            workflowService.claimTask(taskId, user.getUsername());
            Clients.showNotification("Tarea reclamada", "info", null, "end_center", 1500);
            refreshTasks();
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
        }
    }

    @Command
    @NotifyChange({"pendingTasks", "statusMessage"})
    public void completeTask(@BindingParam("taskId") String taskId) {
        if (workflowService == null || user == null) return;
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("aprobado", true);
            vars.put("comentario", "Aprobado por " + user.getUsername());
            workflowService.completeTask(taskId, vars);
            Clients.showNotification("Tarea completada", "info", null, "end_center", 1500);
            refreshTasks();
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
        }
    }

    @Command
    @NotifyChange("showStartForm")
    public void toggleStartForm() {
        showStartForm = !showStartForm;
    }

    @Command
    @NotifyChange({"pendingTasks", "statusMessage", "showStartForm",
            "orderId", "cliente", "vendedor", "monto", "descripcion"})
    public void startApproval() {
        if (workflowService == null || user == null) return;
        if (orderId == null || orderId.isBlank()) {
            Clients.showNotification("Ingrese el ID del pedido", "warning", null, "middle_center", 2000);
            return;
        }
        if (monto == null || monto <= 0) {
            Clients.showNotification("Ingrese un monto válido", "warning", null, "middle_center", 2000);
            return;
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("orderId", orderId);
            vars.put("cliente", cliente != null ? cliente : "");
            vars.put("vendedor", vendedor != null ? vendedor : user.getUsername());
            vars.put("monto", monto != null ? monto : 0.0);
            vars.put("descripcion", descripcion != null ? descripcion : "");

            String processId = workflowService.startProcess(
                    "venta-aprobacion", user.getTenantId(), orderId, vars);

            Clients.showNotification("Proceso iniciado: " + processId, "info", null, "end_center", 2500);

            // Limpiar formulario
            orderId = null; cliente = null; vendedor = null; monto = null; descripcion = null;
            showStartForm = false;
            refreshTasks();
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
        }
    }

    // ── Getters / Setters ──

    public List<TaskResponse> getPendingTasks() { return pendingTasks; }
    public String getStatusMessage() { return statusMessage; }
    public boolean isShowStartForm() { return showStartForm; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getVendedor() { return vendedor; }
    public void setVendedor(String vendedor) { this.vendedor = vendedor; }
    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
