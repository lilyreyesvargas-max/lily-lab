package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.tenancy.RoleSchemaService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.platform.Tenant;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.core.tenancy.platform.TenantSchema;
import com.lreyes.platform.core.tenancy.platform.TenantSchemaService;
import com.lreyes.platform.ui.zk.model.MenuItem;
import com.lreyes.platform.ui.zk.model.SchemaMenuRegistry;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.AfterCompose;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.ContextParam;
import org.zkoss.bind.annotation.ContextType;
import org.zkoss.bind.annotation.GlobalCommand;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zkplus.spring.SpringUtil;
import org.zkoss.zul.Include;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ViewModel del layout principal (shell).
 * <p>
 * Controla:
 * <ul>
 *   <li>Menu lateral (filtrado por schemas del tenant + permisos del rol)</li>
 *   <li>Selector de tenant en el header (solo para platform admin)</li>
 *   <li>Navegacion (cambia el {@code <include>} del centro)</li>
 *   <li>Logout</li>
 * </ul>
 */
@VariableResolver(DelegatingVariableResolver.class)
public class LayoutVM {

    @WireVariable
    private TenantRegistryService tenantRegistryService;

    private TenantSchemaService tenantSchemaService;
    private RoleSchemaService roleSchemaService;

    private UiUser user;
    private List<MenuItem> menuItems;
    private MenuItem selectedMenu;
    private String currentPage;
    private List<Tenant> tenants;
    private Tenant selectedTenant;
    private String currentTenant;
    private String headerColor;
    private String sidebarColor;
    private String logoUrl;
    private String headerTextColor;
    private String sidebarTextColor;
    private Include mainInclude;

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
        findInclude(view);
    }

    private void findInclude(Component comp) {
        if (mainInclude != null) return;
        if (comp instanceof Include) {
            mainInclude = (Include) comp;
            return;
        }
        for (Component child : comp.getChildren()) {
            findInclude(child);
        }
    }

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null) {
            Executions.sendRedirect("/zul/login.zul");
            return;
        }

        tenantSchemaService = SpringUtil.getApplicationContext().getBean(TenantSchemaService.class);
        roleSchemaService = SpringUtil.getApplicationContext().getBean(RoleSchemaService.class);

        // Cargar tenants desde BD
        tenants = tenantRegistryService.findAllActive();

        if (user.isPlatformAdmin()) {
            // Preservar tenant si ya fue seleccionado (ej. despues de changeTenant redirect)
            if (user.getTenantId() != null) {
                selectedTenant = tenants.stream()
                        .filter(t -> t.getName().equals(user.getTenantId()))
                        .findFirst().orElse(null);
            }
            if (selectedTenant == null && !tenants.isEmpty()) {
                selectedTenant = tenants.get(0);
            }
            currentTenant = selectedTenant != null ? selectedTenant.getName() : null;
            // Actualizar sesion con el tenant seleccionado
            if (currentTenant != null && !currentTenant.equals(user.getTenantId())) {
                user = new UiUser(user.getUsername(), currentTenant, user.getRoles(), true);
                Sessions.getCurrent().setAttribute("user", user);
            }
        } else {
            currentTenant = user.getTenantId();
            selectedTenant = tenants.stream()
                    .filter(t -> t.getName().equals(currentTenant))
                    .findFirst().orElse(null);
        }

        // Establecer tenant en contexto
        if (currentTenant != null) {
            TenantContext.setCurrentTenant(currentTenant);
        }

        // Cargar branding del tenant actual
        loadBranding();

        buildMenu();

        // Si venimos de un cambio de tenant, restaurar la pagina previa
        String resumePage = (String) Sessions.getCurrent().getAttribute("resumePage");
        if (resumePage != null) {
            currentPage = resumePage;
            Sessions.getCurrent().removeAttribute("resumePage");
        } else {
            currentPage = "~./zul/dashboard.zul";
        }
    }

    private void loadBranding() {
        headerColor = "#2c3e50";
        sidebarColor = "#34495e";
        logoUrl = null;

        if (currentTenant != null) {
            tenantRegistryService.findByName(currentTenant).ifPresent(tenant -> {
                if (tenant.getPrimaryColor() != null && !tenant.getPrimaryColor().isBlank()) {
                    headerColor = tenant.getPrimaryColor();
                    sidebarColor = darkenColor(headerColor, 0.85);
                }
                if (tenant.getLogoPath() != null && !tenant.getLogoPath().isBlank()) {
                    logoUrl = "/api/logos/" + tenant.getLogoPath();
                }
            });
        }

        headerTextColor = isLightColor(headerColor) ? "#222222" : "#ffffff";
        sidebarTextColor = isLightColor(sidebarColor) ? "#222222" : "#ffffff";
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

    private String darkenColor(String hex, double factor) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = (int) (Integer.parseInt(h.substring(0, 2), 16) * factor);
            int g = (int) (Integer.parseInt(h.substring(2, 4), 16) * factor);
            int b = (int) (Integer.parseInt(h.substring(4, 6), 16) * factor);
            return String.format("#%02x%02x%02x", Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
        } catch (Exception e) {
            return "#34495e";
        }
    }

    private void buildMenu() {
        menuItems = new ArrayList<>();

        if (user == null || currentTenant == null) {
            return;
        }

        // Asegurar tenant context para queries JPA
        TenantContext.setCurrentTenant(currentTenant);

        // Obtener schema_types del tenant actual (desde platform.tenant_schemas via JDBC)
        Set<String> tenantSchemaTypes = getTenantSchemaTypes();

        // Determinar schema_types visibles segun rol
        Set<String> allowedSchemaTypes;

        if (user.isPlatformAdmin() || user.hasRole(RoleConstants.ADMIN)) {
            // Platform admin y admin ven TODOS los schemas del tenant
            allowedSchemaTypes = tenantSchemaTypes;
        } else {
            // Otros roles: solo los schema_types asignados via role_schemas (JPA)
            Set<String> roleSchemaTypes = roleSchemaService.getSchemaTypesForRoles(user.getRoles());
            // Intersectar con los schemas que realmente tiene el tenant
            allowedSchemaTypes = roleSchemaTypes.stream()
                    .filter(tenantSchemaTypes::contains)
                    .collect(Collectors.toSet());
        }

        // Dashboard SIEMPRE visible (no depende de schema)
        menuItems.add(SchemaMenuRegistry.getDashboardItem());

        // Items de modulos segun schemas permitidos
        menuItems.addAll(SchemaMenuRegistry.getMenuItems(allowedSchemaTypes));

        // Items de admin del tenant
        if (user.hasRole(RoleConstants.ADMIN) || user.isPlatformAdmin()) {
            menuItems.addAll(SchemaMenuRegistry.getAdminItems());
        }

        // Items de platform admin
        if (user.isPlatformAdmin()) {
            menuItems.addAll(SchemaMenuRegistry.getPlatformAdminItems());
        }
    }

    /**
     * Obtiene los schema_types activos del tenant actual desde la tabla platform.tenant_schemas.
     */
    private Set<String> getTenantSchemaTypes() {
        return tenantRegistryService.findByName(currentTenant)
                .map(tenant -> tenantSchemaService.findByTenantId(tenant.getId()).stream()
                        .filter(TenantSchema::isActive)
                        .map(TenantSchema::getSchemaType)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Command
    @NotifyChange("currentPage")
    public void navigate() {
        if (selectedMenu != null) {
            currentPage = selectedMenu.getPage();
        }
    }

    @GlobalCommand
    @NotifyChange("currentPage")
    public void navigateTo(@BindingParam("page") String page) {
        if (page != null) {
            currentPage = page;
        }
    }

    @GlobalCommand
    @NotifyChange("currentPage")
    public void navigateForceReload(@BindingParam("page") String page) {
        if (page != null && mainInclude != null) {
            mainInclude.setSrc(null);
            mainInclude.setSrc(page);
            currentPage = page;
        }
    }

    @Command
    public void changeTenant() {
        if (selectedTenant != null && user != null && user.isPlatformAdmin()) {
            currentTenant = selectedTenant.getName();
            UiUser updated = new UiUser(user.getUsername(), currentTenant, user.getRoles(), true);
            Sessions.getCurrent().setAttribute("user", updated);
            user = updated;
            TenantContext.setCurrentTenant(currentTenant);
            Sessions.getCurrent().setAttribute("resumePage", currentPage);
            Clients.evalJavaScript("window.location.replace('/zul/index.zul?_t=" + System.currentTimeMillis() + "')");
        }
    }

    @Command
    public void logout() {
        Sessions.getCurrent().removeAttribute("user");
        Executions.sendRedirect("/zul/login.zul");
        Sessions.getCurrent().invalidate();
    }

    // -- Getters / Setters --

    public String getDisplayUser() {
        return user != null ? user.getDisplayName() : "";
    }

    public List<String> getUserRoles() {
        return user != null ? user.getRoles() : List.of();
    }

    public boolean isShowTenantSwitcher() {
        return user != null && user.isPlatformAdmin();
    }

    public String getTenantLabel() {
        return selectedTenant != null && selectedTenant.getDisplayName() != null
                ? selectedTenant.getDisplayName() : (currentTenant != null ? currentTenant : "");
    }

    public List<MenuItem> getMenuItems() { return menuItems; }
    public MenuItem getSelectedMenu() { return selectedMenu; }
    public void setSelectedMenu(MenuItem selectedMenu) { this.selectedMenu = selectedMenu; }
    public String getCurrentPage() { return currentPage; }
    public List<Tenant> getTenants() { return tenants; }
    public Tenant getSelectedTenant() { return selectedTenant; }
    public void setSelectedTenant(Tenant selectedTenant) { this.selectedTenant = selectedTenant; }
    public String getCurrentTenant() { return currentTenant; }
    public String getHeaderColor() { return headerColor; }
    public String getSidebarColor() { return sidebarColor; }
    public String getLogoUrl() { return logoUrl; }
    public String getHeaderTextColor() { return headerTextColor; }
    public String getSidebarTextColor() { return sidebarTextColor; }
}
