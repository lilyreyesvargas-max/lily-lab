package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.User;
import com.lreyes.platform.ui.zk.ZkSecurityHelper;
import com.lreyes.platform.core.tenancy.UserRepository;
import com.lreyes.platform.core.tenancy.UserService;
import com.lreyes.platform.modules.employees.EmployeeServicePort;
import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.modules.employees.dto.EmployeeResponse;
import com.lreyes.platform.modules.employees.dto.UpdateEmployeeRequest;
import com.lreyes.platform.ui.zk.model.EmployeeItem;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.springframework.data.domain.Pageable;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class EmployeeListVM {

    private EmployeeServicePort employeeService;
    private UiUser user;

    private List<EmployeeItem> employees = new ArrayList<>();
    private List<EmployeeItem> allEmployees = new ArrayList<>();
    private EmployeeItem selectedEmployee;
    private EmployeeItem editingEmployee;
    private String searchTerm;
    private boolean editing;
    private boolean newRecord;
    private String initialPassword;

    @Init
    public void init() {
        user = ZkSecurityHelper.requireAuthenticated();
        employeeService = SpringUtil.getApplicationContext().getBean(EmployeeServicePort.class);
        loadData();
    }

    private void loadData() {
        TenantContext.setCurrentTenant(user.getTenantId());
        List<EmployeeResponse> responses = employeeService
                .findAll(null, Pageable.unpaged()).content();
        allEmployees = new ArrayList<>();
        for (EmployeeResponse r : responses) {
            allEmployees.add(new EmployeeItem(
                    r.id().toString(), r.firstName(), r.lastName(),
                    r.email(), r.position()));
        }
        employees = new ArrayList<>(allEmployees);
    }

    @Command
    @NotifyChange("employees")
    public void search() {
        if (searchTerm == null || searchTerm.isBlank()) {
            employees = new ArrayList<>(allEmployees);
        } else {
            String term = searchTerm.toLowerCase();
            employees = allEmployees.stream()
                    .filter(e -> e.getFullName().toLowerCase().contains(term)
                            || (e.getEmail() != null && e.getEmail().toLowerCase().contains(term))
                            || (e.getPosition() != null && e.getPosition().toLowerCase().contains(term)))
                    .toList();
        }
    }

    @Command
    @NotifyChange({"editing", "editingEmployee", "newRecord", "initialPassword"})
    public void openNew() {
        editingEmployee = new EmployeeItem();
        initialPassword = null;
        editing = true;
        newRecord = true;
    }

    @Command
    @NotifyChange({"editing", "editingEmployee", "newRecord"})
    public void edit(@BindingParam("employee") EmployeeItem e) {
        editingEmployee = new EmployeeItem(
                e.getId(), e.getFirstName(), e.getLastName(), e.getEmail(), e.getPosition());
        editing = true;
        newRecord = false;
    }

    @Command
    @NotifyChange({"employees", "editing", "editingEmployee"})
    public void save() {
        if (editingEmployee.getFirstName() == null || editingEmployee.getFirstName().isBlank()) {
            Clients.showNotification("El nombre es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }

        String email = editingEmployee.getEmail();
        if (email == null || email.isBlank() || !email.matches(".+@.+\\..+")) {
            Clients.showNotification("Ingrese un email válido", "warning", null, "middle_center", 2000);
            return;
        }

        TenantContext.setCurrentTenant(user.getTenantId());
        if (newRecord) {
            if (initialPassword == null || initialPassword.isBlank()) {
                Clients.showNotification("La contraseña es obligatoria para nuevos empleados", "warning", null, "middle_center", 2000);
                return;
            }

            employeeService.create(new CreateEmployeeRequest(
                    editingEmployee.getFirstName(),
                    editingEmployee.getLastName(),
                    editingEmployee.getEmail(),
                    editingEmployee.getPosition(),
                    null, null));

            // Crear usuario automáticamente para el tenant
            try {
                String username = email.substring(0, email.indexOf('@'));
                UserService uService = SpringUtil.getApplicationContext().getBean(UserService.class);
                UserRepository userRepository = SpringUtil.getApplicationContext().getBean(UserRepository.class);
                TenantContext.setCurrentTenant(user.getTenantId());
                if (!userRepository.existsByUsername(username)) {
                    User newUser = new User();
                    newUser.setUsername(username);
                    newUser.setEmail(email);
                    String fullName = editingEmployee.getFirstName()
                            + (editingEmployee.getLastName() != null ? " " + editingEmployee.getLastName() : "");
                    newUser.setFullName(fullName);
                    newUser.setEnabled(true);
                    uService.create(newUser, initialPassword, Collections.emptySet());
                    Clients.showNotification("Empleado y usuario '" + username + "' creados", "info", null, "end_center", 2000);
                } else {
                    Clients.showNotification("Empleado creado (usuario '" + username + "' ya existía)", "info", null, "end_center", 2000);
                }
            } catch (Exception ex) {
                Clients.showNotification("Empleado creado, pero error al crear usuario: " + ex.getMessage(),
                        "error", null, "middle_center", 5000);
            }
        } else {
            employeeService.update(
                    UUID.fromString(editingEmployee.getId()),
                    new UpdateEmployeeRequest(
                            editingEmployee.getFirstName(),
                            editingEmployee.getLastName(),
                            editingEmployee.getEmail(),
                            editingEmployee.getPosition(),
                            null, null, null));
            Clients.showNotification("Empleado actualizado", "info", null, "end_center", 1500);
        }

        loadData();
        editing = false;
        editingEmployee = null;
    }

    @Command
    @NotifyChange({"editing", "editingEmployee"})
    public void cancelEdit() {
        editing = false;
        editingEmployee = null;
    }

    @Command
    @NotifyChange("employees")
    public void delete(@BindingParam("employee") EmployeeItem e) {
        TenantContext.setCurrentTenant(user.getTenantId());
        employeeService.delete(UUID.fromString(e.getId()));
        loadData();
        Clients.showNotification("Empleado eliminado", "info", null, "end_center", 1500);
    }

    // ── Getters / Setters ──

    public String getFormTitle() { return newRecord ? "Nuevo Empleado" : "Editar Empleado"; }
    public List<EmployeeItem> getEmployees() { return employees; }
    public EmployeeItem getSelectedEmployee() { return selectedEmployee; }
    public void setSelectedEmployee(EmployeeItem e) { this.selectedEmployee = e; }
    public EmployeeItem getEditingEmployee() { return editingEmployee; }
    public void setEditingEmployee(EmployeeItem e) { this.editingEmployee = e; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public boolean isEditing() { return editing; }
    public boolean isNewRecord() { return newRecord; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
}
