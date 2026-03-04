package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.User;
import com.lreyes.platform.core.tenancy.UserService;
import com.lreyes.platform.core.tenancy.platform.PlatformUser;
import com.lreyes.platform.core.tenancy.platform.PlatformUserService;
import com.lreyes.platform.core.tenancy.platform.Tenant;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ViewModel para la pantalla de login (perfiles dev/local).
 * <p>
 * Soporta dos modos:
 * <ul>
 *   <li><b>Tenant login:</b> usuario predefinido + selección de tenant</li>
 *   <li><b>Platform login:</b> username + password contra {@code platform_users}</li>
 * </ul>
 */
@VariableResolver(DelegatingVariableResolver.class)
public class LoginVM {

    @WireVariable
    private PlatformUserService platformUserService;

    @WireVariable
    private TenantRegistryService tenantRegistryService;

    @WireVariable
    private UserService userService;

    private String username;
    private String password;
    private String selectedTenant;
    private List<String> tenants;
    private String errorMessage;
    private boolean platformLogin;

    // Branding del tenant seleccionado
    private static final String DEFAULT_COLOR = "#2c3e50";
    private String brandingColor;
    private String brandingTextColor;
    private String brandingLogoUrl;

    @Init
    public void init() {
        tenants = tenantRegistryService.getActiveTenantNames();
        if (!tenants.isEmpty()) {
            selectedTenant = tenants.get(0);
        }
        platformLogin = false;
        loadBranding();

        // Si ya hay sesión activa, redirigir al index
        UiUser existing = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (existing != null) {
            Executions.sendRedirect("/zul/index.zul");
        }
    }

    @Command
    @NotifyChange({"brandingColor", "brandingTextColor", "brandingLogoUrl"})
    public void onTenantChange() {
        loadBranding();
    }

    private void loadBranding() {
        brandingColor = DEFAULT_COLOR;
        brandingTextColor = "#ffffff";
        brandingLogoUrl = null;

        if (selectedTenant != null && !platformLogin) {
            tenantRegistryService.findByName(selectedTenant).ifPresent(tenant -> {
                if (tenant.getPrimaryColor() != null && !tenant.getPrimaryColor().isBlank()) {
                    brandingColor = tenant.getPrimaryColor();
                    brandingTextColor = isLightColor(brandingColor) ? "#222222" : "#ffffff";
                }
                if (tenant.getLogoPath() != null && !tenant.getLogoPath().isBlank()) {
                    brandingLogoUrl = "/api/logos/" + tenant.getLogoPath();
                }
            });
        }
    }

    private boolean isLightColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            double luminance = (r * 299.0 + g * 587.0 + b * 114.0) / 1000.0;
            return luminance > 150;
        } catch (Exception e) {
            return false;
        }
    }

    @Command
    @NotifyChange("errorMessage")
    public void login() {
        if (username == null || username.isBlank()) {
            errorMessage = "Ingrese un nombre de usuario";
            return;
        }

        if (platformLogin) {
            loginPlatformAdmin();
        } else {
            loginTenantUser();
        }
    }

    private void loginPlatformAdmin() {
        if (password == null || password.isBlank()) {
            errorMessage = "Ingrese la contraseña";
            return;
        }

        String user = username.trim().toLowerCase();
        Optional<PlatformUser> result = platformUserService.authenticate(user, password);

        if (result.isEmpty()) {
            errorMessage = "Credenciales inválidas o usuario deshabilitado";
            return;
        }

        List<String> roles = List.of(RoleConstants.PLATFORM_ADMIN, RoleConstants.ADMIN);
        UiUser uiUser = new UiUser(user, null, roles, true);
        Sessions.getCurrent().setAttribute("user", uiUser);

        Clients.showNotification("Bienvenido, " + user + " [PLATFORM]", "info", null, "middle_center", 1500);
        Executions.sendRedirect("/zul/index.zul");
    }

    private void loginTenantUser() {
        if (selectedTenant == null || selectedTenant.isBlank()) {
            errorMessage = "Seleccione un tenant";
            return;
        }
        if (password == null || password.isBlank()) {
            errorMessage = "Ingrese la contraseña";
            return;
        }

        String user = username.trim().toLowerCase();

        // Establecer el tenant para que Hibernate use el schema correcto
        TenantContext.setCurrentTenant(selectedTenant);
        try {
            Optional<User> result = userService.authenticate(user, password);
            if (result.isEmpty()) {
                errorMessage = "Credenciales inválidas o usuario deshabilitado";
                return;
            }

            User dbUser = result.get();
            List<String> roles = dbUser.getRoles().stream()
                    .map(r -> r.getName())
                    .collect(Collectors.toList());

            UiUser uiUser = new UiUser(dbUser.getUsername(), selectedTenant, roles);
            Sessions.getCurrent().setAttribute("user", uiUser);

            Clients.showNotification("Bienvenido, " + dbUser.getFullName(), "info", null, "middle_center", 1500);
            Executions.sendRedirect("/zul/index.zul");
        } finally {
            TenantContext.clear();
        }
    }

    @Command
    @NotifyChange({"platformLogin", "errorMessage", "brandingColor", "brandingTextColor", "brandingLogoUrl"})
    public void togglePlatformLogin() {
        platformLogin = !platformLogin;
        errorMessage = null;
        loadBranding();
    }

    // ── Getters / Setters ──

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSelectedTenant() { return selectedTenant; }
    public void setSelectedTenant(String selectedTenant) { this.selectedTenant = selectedTenant; }
    public List<String> getTenants() { return tenants; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isPlatformLogin() { return platformLogin; }
    public String getBrandingColor() { return brandingColor; }
    public String getBrandingTextColor() { return brandingTextColor; }
    public String getBrandingLogoUrl() { return brandingLogoUrl; }
}
