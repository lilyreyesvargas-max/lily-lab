package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.platform.Tenant;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.core.tenancy.platform.TenantSchema;
import com.lreyes.platform.core.tenancy.platform.TenantSchemaService;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel para gestión de schemas por tenant (solo platform admin).
 */
@VariableResolver(DelegatingVariableResolver.class)
public class TenantSchemaListVM {

    @WireVariable
    private TenantRegistryService tenantRegistryService;

    @WireVariable
    private TenantSchemaService tenantSchemaService;

    private Tenant currentTenant;
    private List<TenantSchema> schemas;
    private boolean showForm;

    // Campos del formulario
    private String formSchemaName;
    private String formSchemaType;

    @Init
    public void init() {
        UiUser user = checkPlatformAdmin();
        // Usar el tenant seleccionado en el banner
        if (user.getTenantId() != null) {
            currentTenant = tenantRegistryService.findByName(user.getTenantId()).orElse(null);
        }
        if (currentTenant != null) {
            schemas = tenantSchemaService.findByTenantId(currentTenant.getId());
        } else {
            schemas = new ArrayList<>();
        }
    }

    @Command
    @NotifyChange({"showForm", "formSchemaName", "formSchemaType"})
    public void newSchema() {
        if (currentTenant == null) {
            Clients.showNotification("No hay tenant seleccionado en el banner", "warning", null, "middle_center", 2000);
            return;
        }
        showForm = true;
        formSchemaName = "";
        formSchemaType = "";
    }

    @Command
    @NotifyChange({"schemas", "showForm"})
    public void saveSchema() {
        if (currentTenant == null) return;
        try {
            tenantSchemaService.createSchema(
                    currentTenant.getId(), formSchemaName, formSchemaType);
            Clients.showNotification("Schema creado exitosamente", "info", null, "middle_center", 2000);
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
            return;
        }
        showForm = false;
        schemas = tenantSchemaService.findByTenantId(currentTenant.getId());
    }

    @Command
    @NotifyChange("showForm")
    public void cancelForm() {
        showForm = false;
    }

    private UiUser checkPlatformAdmin() {
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null || !user.isPlatformAdmin()) {
            throw new SecurityException("Acceso denegado: se requiere platform_admin");
        }
        return user;
    }

    // ── Getters / Setters ──

    public List<TenantSchema> getSchemas() { return schemas; }
    public boolean isShowForm() { return showForm; }
    public String getFormSchemaName() { return formSchemaName; }
    public void setFormSchemaName(String formSchemaName) { this.formSchemaName = formSchemaName; }
    public String getFormSchemaType() { return formSchemaType; }
    public void setFormSchemaType(String formSchemaType) { this.formSchemaType = formSchemaType; }
}
