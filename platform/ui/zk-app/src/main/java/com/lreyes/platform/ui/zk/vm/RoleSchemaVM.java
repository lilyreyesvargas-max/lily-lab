package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.Role;
import com.lreyes.platform.core.tenancy.RoleSchemaService;
import com.lreyes.platform.core.tenancy.RoleService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.core.tenancy.platform.TenantSchema;
import com.lreyes.platform.core.tenancy.platform.TenantSchemaService;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ViewModel para la vista de permisos de schemas por rol.
 * Permite al admin del tenant asignar schema_types a cada rol.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class RoleSchemaVM {

    private RoleService roleService;
    private RoleSchemaService roleSchemaService;
    private TenantSchemaService tenantSchemaService;
    private TenantRegistryService tenantRegistryService;

    private UiUser user;
    private List<Role> roles;
    private Role selectedRole;
    private List<String> tenantSchemaTypes;
    private Set<String> selectedSchemaSet;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        roleService = SpringUtil.getApplicationContext().getBean(RoleService.class);
        roleSchemaService = SpringUtil.getApplicationContext().getBean(RoleSchemaService.class);
        tenantSchemaService = SpringUtil.getApplicationContext().getBean(TenantSchemaService.class);
        tenantRegistryService = SpringUtil.getApplicationContext().getBean(TenantRegistryService.class);

        TenantContext.setCurrentTenant(user.getTenantId());
        roles = roleService.findAll();
        tenantSchemaTypes = loadTenantSchemaTypes();
        selectedSchemaSet = new LinkedHashSet<>();
    }

    private List<String> loadTenantSchemaTypes() {
        String tenantName = user.getTenantId();
        return tenantRegistryService.findByName(tenantName)
                .map(tenant -> tenantSchemaService.findByTenantId(tenant.getId()).stream()
                        .filter(TenantSchema::isActive)
                        .map(TenantSchema::getSchemaType)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Command
    @NotifyChange("selectedSchemaSet")
    public void selectRole() {
        if (selectedRole != null) {
            TenantContext.setCurrentTenant(user.getTenantId());
            List<String> types = roleSchemaService.getSchemaTypesForRole(selectedRole.getId());
            selectedSchemaSet = new LinkedHashSet<>(types);
        } else {
            selectedSchemaSet = new LinkedHashSet<>();
        }
    }

    @Command
    public void save() {
        if (selectedRole == null) {
            Clients.showNotification("Seleccione un rol primero", "warning", null, "middle_center", 3000);
            return;
        }
        TenantContext.setCurrentTenant(user.getTenantId());
        roleSchemaService.saveAssignments(selectedRole.getId(), new ArrayList<>(selectedSchemaSet));
        Clients.showNotification("Permisos guardados para rol: " + selectedRole.getName(),
                "info", null, "middle_center", 3000);
    }

    // -- Getters / Setters --

    public List<Role> getRoles() { return roles; }
    public Role getSelectedRole() { return selectedRole; }
    public void setSelectedRole(Role selectedRole) { this.selectedRole = selectedRole; }
    public List<String> getTenantSchemaTypes() { return tenantSchemaTypes; }
    public Set<String> getSelectedSchemaSet() { return selectedSchemaSet; }
    public void setSelectedSchemaSet(Set<String> selectedSchemaSet) { this.selectedSchemaSet = selectedSchemaSet; }
}
