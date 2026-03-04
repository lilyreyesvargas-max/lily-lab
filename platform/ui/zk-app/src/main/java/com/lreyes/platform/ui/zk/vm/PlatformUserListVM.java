package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.platform.PlatformUser;
import com.lreyes.platform.core.tenancy.platform.PlatformUserService;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.util.List;
import java.util.UUID;

/**
 * ViewModel para gestión de platform admin users (solo platform admin).
 */
@VariableResolver(DelegatingVariableResolver.class)
public class PlatformUserListVM {

    @WireVariable
    private PlatformUserService platformUserService;

    private List<PlatformUser> users;
    private PlatformUser selectedUser;
    private boolean showForm;
    private boolean editing;

    // Campos del formulario
    private String formUsername;
    private String formPassword;
    private String formEmail;
    private String formFullName;
    private boolean formEnabled;

    @Init
    public void init() {
        checkPlatformAdmin();
        loadUsers();
    }

    @Command
    @NotifyChange({"users", "selectedUser"})
    public void refresh() {
        loadUsers();
    }

    @Command
    @NotifyChange({"showForm", "editing", "formUsername", "formPassword", "formEmail", "formFullName", "formEnabled"})
    public void newUser() {
        showForm = true;
        editing = false;
        formUsername = "";
        formPassword = "";
        formEmail = "";
        formFullName = "";
        formEnabled = true;
    }

    @Command
    @NotifyChange({"showForm", "editing", "formUsername", "formPassword", "formEmail", "formFullName", "formEnabled"})
    public void editUser() {
        if (selectedUser == null) return;
        showForm = true;
        editing = true;
        formUsername = selectedUser.getUsername();
        formPassword = "";
        formEmail = selectedUser.getEmail();
        formFullName = selectedUser.getFullName();
        formEnabled = selectedUser.isEnabled();
    }

    @Command
    @NotifyChange({"users", "showForm", "selectedUser"})
    public void saveUser() {
        try {
            if (editing && selectedUser != null) {
                platformUserService.update(
                        selectedUser.getId(), formEmail, formFullName, formEnabled);
                if (formPassword != null && !formPassword.isBlank()) {
                    platformUserService.changePassword(selectedUser.getId(), formPassword);
                }
                Clients.showNotification("Usuario actualizado", "info", null, "middle_center", 1500);
            } else {
                if (formPassword == null || formPassword.isBlank()) {
                    Clients.showNotification("La contraseña es requerida", "warning", null, "middle_center", 2000);
                    return;
                }
                platformUserService.create(formUsername, formPassword, formEmail, formFullName);
                Clients.showNotification("Usuario creado exitosamente", "info", null, "middle_center", 2000);
            }
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
            return;
        }
        showForm = false;
        selectedUser = null;
        loadUsers();
    }

    @Command
    @NotifyChange({"users", "selectedUser"})
    public void deleteUser() {
        if (selectedUser == null) return;
        try {
            platformUserService.delete(selectedUser.getId());
            Clients.showNotification("Usuario eliminado", "info", null, "middle_center", 1500);
        } catch (Exception e) {
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 3000);
        }
        selectedUser = null;
        loadUsers();
    }

    @Command
    @NotifyChange("showForm")
    public void cancelForm() {
        showForm = false;
    }

    private void loadUsers() {
        users = platformUserService.findAll();
        selectedUser = null;
    }

    private void checkPlatformAdmin() {
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null || !user.isPlatformAdmin()) {
            throw new SecurityException("Acceso denegado: se requiere platform_admin");
        }
    }

    // ── Getters / Setters ──

    public List<PlatformUser> getUsers() { return users; }
    public PlatformUser getSelectedUser() { return selectedUser; }
    public void setSelectedUser(PlatformUser selectedUser) { this.selectedUser = selectedUser; }
    public boolean isShowForm() { return showForm; }
    public boolean isEditing() { return editing; }
    public String getFormUsername() { return formUsername; }
    public void setFormUsername(String formUsername) { this.formUsername = formUsername; }
    public String getFormPassword() { return formPassword; }
    public void setFormPassword(String formPassword) { this.formPassword = formPassword; }
    public String getFormEmail() { return formEmail; }
    public void setFormEmail(String formEmail) { this.formEmail = formEmail; }
    public String getFormFullName() { return formFullName; }
    public void setFormFullName(String formFullName) { this.formFullName = formFullName; }
    public boolean isFormEnabled() { return formEnabled; }
    public void setFormEnabled(boolean formEnabled) { this.formEnabled = formEnabled; }
}
