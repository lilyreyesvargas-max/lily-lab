package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.tenancy.platform.Tenant;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.ui.zk.model.UiUser;
import lombok.extern.slf4j.Slf4j;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.util.media.Media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * ViewModel para gestión de tenants (solo platform admin).
 */
@VariableResolver(DelegatingVariableResolver.class)
@Slf4j
public class TenantListVM {

    private static final Path LOGOS_DIR = Paths.get("data", "logos");

    @WireVariable
    private TenantRegistryService tenantRegistryService;

    private List<Tenant> tenants;
    private Tenant selectedTenant;
    private boolean showForm;
    private boolean editing;

    // Campos del formulario
    private String formName;
    private String formDisplayName;
    private boolean formActive;
    private String formPrimaryColor;
    private String formLogoPath;

    @Init
    public void init() {
        checkPlatformAdmin();
        loadTenants();
    }

    @Command
    @NotifyChange({"tenants", "selectedTenant"})
    public void refresh() {
        loadTenants();
    }

    @Command
    @NotifyChange({"showForm", "editing", "formName", "formDisplayName", "formActive", "formPrimaryColor", "formLogoPath"})
    public void newTenant() {
        showForm = true;
        editing = false;
        formName = "";
        formDisplayName = "";
        formActive = true;
        formPrimaryColor = "";
        formLogoPath = null;
    }

    @Command
    @NotifyChange({"showForm", "editing", "formName", "formDisplayName", "formActive", "formPrimaryColor", "formLogoPath"})
    public void editTenant() {
        if (selectedTenant == null) return;
        showForm = true;
        editing = true;
        formName = selectedTenant.getName();
        formDisplayName = selectedTenant.getDisplayName();
        formActive = selectedTenant.isActive();
        formPrimaryColor = selectedTenant.getPrimaryColor() != null ? selectedTenant.getPrimaryColor() : "";
        formLogoPath = selectedTenant.getLogoPath();
    }

    @Command
    @NotifyChange({"tenants", "showForm", "selectedTenant"})
    public void saveTenant() {
        String color = (formPrimaryColor != null && !formPrimaryColor.isBlank()) ? formPrimaryColor.trim() : null;
        try {
            if (editing && selectedTenant != null) {
                tenantRegistryService.updateTenant(
                        selectedTenant.getId(), formDisplayName, formActive, color, formLogoPath);
                Clients.showNotification("Tenant actualizado", "info", null, "middle_center", 1500);
            } else {
                Tenant created = tenantRegistryService.createTenant(formName, formDisplayName);
                if (color != null || formLogoPath != null) {
                    tenantRegistryService.updateTenant(created.getId(), formDisplayName, true, color, formLogoPath);
                }
                Clients.showNotification("Tenant creado exitosamente", "info", null, "middle_center", 2000);
            }
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
            return;
        }
        showForm = false;
        selectedTenant = null;
        loadTenants();
    }

    private static final List<String> ALLOWED_EXTENSIONS = List.of("png", "jpg", "jpeg", "svg");

    @Command
    @NotifyChange("formLogoPath")
    public void uploadLogo(@BindingParam("media") Media media) {
        if (media == null) return;

        String tenantName = editing ? formName : formName.trim().toLowerCase();
        if (tenantName == null || tenantName.isBlank()) {
            Clients.showNotification("Ingrese el nombre del tenant primero", "warning", null, "middle_center", 2000);
            return;
        }

        String ext = "png";
        String name = media.getName();
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            Clients.showNotification("Formato no soportado. Use: PNG, JPG o SVG", "warning", null, "middle_center", 3000);
            return;
        }

        String filename = tenantName + "." + ext;

        try {
            Files.createDirectories(LOGOS_DIR);
            Path target = LOGOS_DIR.resolve(filename);
            if (media.isBinary()) {
                Files.copy(media.getStreamData(), target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.writeString(target, media.getStringData());
            }
            formLogoPath = filename;
            Clients.showNotification("Logo cargado", "info", null, "middle_center", 1500);
        } catch (IOException e) {
            log.error("Error guardando logo para tenant '{}'", tenantName, e);
            Clients.showNotification("Error al guardar logo: " + e.getMessage(), "error", null, "middle_center", 3000);
        }
    }

    @Command
    @NotifyChange("showForm")
    public void cancelForm() {
        showForm = false;
    }

    private void loadTenants() {
        tenants = tenantRegistryService.findAll();
        selectedTenant = null;
    }

    private void checkPlatformAdmin() {
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null || !user.isPlatformAdmin()) {
            throw new SecurityException("Acceso denegado: se requiere platform_admin");
        }
    }

    // ── Getters / Setters ──

    public List<Tenant> getTenants() { return tenants; }
    public Tenant getSelectedTenant() { return selectedTenant; }
    public void setSelectedTenant(Tenant selectedTenant) { this.selectedTenant = selectedTenant; }
    public boolean isShowForm() { return showForm; }
    public boolean isEditing() { return editing; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getFormDisplayName() { return formDisplayName; }
    public void setFormDisplayName(String formDisplayName) { this.formDisplayName = formDisplayName; }
    public boolean isFormActive() { return formActive; }
    public void setFormActive(boolean formActive) { this.formActive = formActive; }
    public String getFormPrimaryColor() { return formPrimaryColor; }
    public void setFormPrimaryColor(String formPrimaryColor) { this.formPrimaryColor = formPrimaryColor; }
    public String getFormLogoPath() { return formLogoPath; }
    public void setFormLogoPath(String formLogoPath) { this.formLogoPath = formLogoPath; }

    public String getLogoPreviewUrl() {
        return formLogoPath != null ? "/api/logos/" + formLogoPath : null;
    }
}
