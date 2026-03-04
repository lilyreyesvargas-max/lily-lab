package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.Role;
import com.lreyes.platform.core.tenancy.RoleService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.ui.zk.model.RoleItem;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class RoleListVM {

    private RoleService roleService;
    private UiUser user;

    private List<RoleItem> roles = new ArrayList<>();
    private List<RoleItem> allRoles = new ArrayList<>();
    private RoleItem selectedRole;
    private RoleItem editingRole;
    private String searchTerm;
    private boolean editing;
    private boolean newRecord;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        roleService = SpringUtil.getApplicationContext().getBean(RoleService.class);
        loadData();
    }

    private void loadData() {
        TenantContext.setCurrentTenant(user.getTenantId());
        List<Role> entities = roleService.findAll();
        allRoles = new ArrayList<>();
        for (Role r : entities) {
            allRoles.add(new RoleItem(r.getId().toString(), r.getName(), r.getDescription()));
        }
        roles = new ArrayList<>(allRoles);
    }

    @Command
    @NotifyChange("roles")
    public void search() {
        if (searchTerm == null || searchTerm.isBlank()) {
            roles = new ArrayList<>(allRoles);
        } else {
            String term = searchTerm.toLowerCase();
            roles = allRoles.stream()
                    .filter(r -> r.getName().toLowerCase().contains(term))
                    .toList();
        }
    }

    @Command
    @NotifyChange({"editing", "editingRole", "newRecord"})
    public void openNew() {
        editingRole = new RoleItem();
        editing = true;
        newRecord = true;
    }

    @Command
    @NotifyChange({"editing", "editingRole", "newRecord"})
    public void edit(@BindingParam("role") RoleItem r) {
        editingRole = new RoleItem(r.getId(), r.getName(), r.getDescription());
        editing = true;
        newRecord = false;
    }

    @Command
    @NotifyChange({"roles", "editing", "editingRole"})
    public void save() {
        if (editingRole.getName() == null || editingRole.getName().isBlank()) {
            Clients.showNotification("El nombre es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }

        TenantContext.setCurrentTenant(user.getTenantId());
        if (newRecord) {
            Role role = new Role();
            role.setName(editingRole.getName());
            role.setDescription(editingRole.getDescription());
            roleService.create(role);
            Clients.showNotification("Rol creado", "info", null, "end_center", 1500);
        } else {
            roleService.update(
                    UUID.fromString(editingRole.getId()),
                    editingRole.getName(),
                    editingRole.getDescription());
            Clients.showNotification("Rol actualizado", "info", null, "end_center", 1500);
        }

        loadData();
        editing = false;
        editingRole = null;
    }

    @Command
    @NotifyChange({"editing", "editingRole"})
    public void cancelEdit() {
        editing = false;
        editingRole = null;
    }

    @Command
    @NotifyChange("roles")
    public void delete(@BindingParam("role") RoleItem r) {
        TenantContext.setCurrentTenant(user.getTenantId());
        roleService.delete(UUID.fromString(r.getId()));
        loadData();
        Clients.showNotification("Rol eliminado", "info", null, "end_center", 1500);
    }

    // ── Getters / Setters ──

    public String getFormTitle() {
        return newRecord ? "Nuevo Rol" : "Editar Rol";
    }

    public List<RoleItem> getRoles() { return roles; }
    public RoleItem getSelectedRole() { return selectedRole; }
    public void setSelectedRole(RoleItem selectedRole) { this.selectedRole = selectedRole; }
    public RoleItem getEditingRole() { return editingRole; }
    public void setEditingRole(RoleItem editingRole) { this.editingRole = editingRole; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public boolean isEditing() { return editing; }
    public boolean isNewRecord() { return newRecord; }
}
