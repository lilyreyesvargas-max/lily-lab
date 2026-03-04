package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.catalogs.Catalog;
import com.lreyes.platform.core.catalogs.CatalogService;
import com.lreyes.platform.core.tenancy.Role;
import com.lreyes.platform.core.tenancy.RoleSchemaService;
import com.lreyes.platform.core.tenancy.RoleService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.User;
import com.lreyes.platform.core.tenancy.UserRepository;
import com.lreyes.platform.core.tenancy.UserService;
import com.lreyes.platform.core.tenancy.platform.PlatformUser;
import com.lreyes.platform.core.tenancy.platform.PlatformUserService;
import com.lreyes.platform.core.tenancy.platform.Tenant;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.core.tenancy.platform.TenantSchema;
import com.lreyes.platform.core.tenancy.platform.TenantSchemaService;
import com.lreyes.platform.core.workflow.WorkflowService;
import com.lreyes.platform.modules.customers.CustomerService;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.employees.EmployeeRepository;
import com.lreyes.platform.modules.employees.EmployeeService;
import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.ui.zk.model.AssistantMessage;
import com.lreyes.platform.ui.zk.model.UiUser;
import com.lreyes.platform.ui.zk.service.AssistantEngine;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.*;
import java.util.stream.Collectors;

@VariableResolver(DelegatingVariableResolver.class)
public class AssistantVM {

    private UiUser user;
    private Set<String> allowedSchemas = Set.of();
    private boolean isAdmin;
    private boolean isPlatformAdmin;
    private final AssistantEngine engine = new AssistantEngine();
    private List<AssistantMessage> messages = new ArrayList<>();
    private String userInput;

    // Servicios (lazy)
    private CustomerService customerService;
    private EmployeeService employeeService;
    private WorkflowService workflowService;
    private UserService userService;
    private UserRepository userRepository;
    private RoleService roleService;
    private CatalogService catalogService;
    private TenantRegistryService tenantRegistryService;
    private TenantSchemaService tenantSchemaService;
    private PlatformUserService platformUserService;
    private EmployeeRepository employeeRepository;
    private RoleSchemaService roleSchemaService;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null) return;

        allowedSchemas = resolveAllowedSchemas();
        isPlatformAdmin = user.isPlatformAdmin();
        isAdmin = isPlatformAdmin || user.hasRole(RoleConstants.ADMIN);

        messages.add(new AssistantMessage(
                "¡Hola " + user.getUsername() + "! Soy tu asistente. Escribe \"ayuda\" para ver qué puedo hacer.",
                false));
    }

    @Command
    @NotifyChange({"messages", "userInput"})
    public void sendMessage() {
        if (userInput == null || userInput.isBlank()) return;

        String input = userInput.trim();
        messages.add(new AssistantMessage(input, true));
        userInput = null;

        List<AssistantEngine.Response> responses = engine.process(input, allowedSchemas, isAdmin, isPlatformAdmin);
        processResponses(responses);
        scrollToBottom();
    }

    private void processResponses(List<AssistantEngine.Response> responses) {
        for (AssistantEngine.Response resp : responses) {
            String text = resp.getText();

            // ── Búsqueda (callback al engine) ──
            if (text.startsWith("EXEC_SEARCH:")) {
                String entityCode = text.substring("EXEC_SEARCH:".length());
                List<Map<String, String>> results = searchEntity(entityCode);
                List<AssistantEngine.Response> followUp = engine.continueWithSearchResults(results);
                processResponses(followUp);
                continue;
            }

            // ── Carga de roles (callback al engine) ──
            if (text.equals("LOAD_ROLES")) {
                List<Map<String, String>> roles = loadAvailableRoles();
                List<AssistantEngine.Response> followUp = engine.continueWithRoles(roles);
                processResponses(followUp);
                continue;
            }

            // ── Ejecución CRUD ──
            if (text.startsWith("EXEC_")) {
                String navigateAfter = resp.getNavigateAfterExec();
                boolean ok = handleExec(text);
                engine.clearFlow();
                if (ok && navigateAfter != null) {
                    postNavigateForceReload(navigateAfter);
                }
                continue;
            }

            // ── Mensaje normal ──
            messages.add(new AssistantMessage(text, false));
            if (resp.getNavigateTo() != null) {
                postNavigate(resp.getNavigateTo());
            }
        }
    }

    @Command
    @NotifyChange("messages")
    public void clearChat() {
        messages.clear();
        engine.clearFlow();
        messages.add(new AssistantMessage(
                "Chat limpiado. Escribe \"ayuda\" para ver las opciones disponibles.", false));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Búsquedas ──
    // ══════════════════════════════════════════════════════════════

    private List<Map<String, String>> loadAvailableRoles() {
        TenantContext.setCurrentTenant(user.getTenantId());
        List<Role> roles = getRoleService().findAll();
        List<Map<String, String>> result = new ArrayList<>();
        for (Role r : roles) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("_id", r.getId().toString());
            m.put("name", r.getName());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, String>> searchEntity(String entityCode) {
        String term = engine.getActiveFlow() != null
                ? engine.getActiveFlow().getData().getOrDefault("searchTerm", "") : "";
        TenantContext.setCurrentTenant(user.getTenantId());

        switch (entityCode) {
            case "ROLE": return searchRoles(term);
            case "USER": return searchUsers(term);
            case "CATALOG": return searchCatalogs(term);
            case "TENANT": return searchTenants(term);
            case "PLATFORM_USER": return searchPlatformUsers(term);
            case "ROLE_PERMS": return searchRolesForPerms(term);
            default: return List.of();
        }
    }

    private List<Map<String, String>> searchRoles(String term) {
        List<Role> roles = getRoleService().findAll();
        String t = term.toLowerCase();
        return roles.stream()
                .filter(r -> t.isEmpty() || r.getName().toLowerCase().contains(t))
                .map(r -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("_id", r.getId().toString());
                    m.put("_display", r.getName() + (r.getDescription() != null ? " — " + r.getDescription() : ""));
                    m.put("name", r.getName());
                    m.put("description", r.getDescription() != null ? r.getDescription() : "");
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, String>> searchUsers(String term) {
        List<User> users = getUserService().findAll();
        String t = term.toLowerCase();
        return users.stream()
                .filter(u -> t.isEmpty() || u.getUsername().toLowerCase().contains(t)
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(t))
                        || u.getEmail().toLowerCase().contains(t))
                .map(u -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("_id", u.getId().toString());
                    m.put("_display", u.getUsername() + " (" + u.getEmail() + ")");
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    m.put("fullName", u.getFullName() != null ? u.getFullName() : "");
                    m.put("enabled", String.valueOf(u.isEnabled()));
                    m.put("password", "");
                    // Roles info (loaded by @EntityGraph)
                    Set<Role> roles = u.getRoles();
                    if (roles != null && !roles.isEmpty()) {
                        m.put("currentRoles", roles.stream().map(Role::getName).collect(Collectors.joining(", ")));
                        m.put("currentRoleIds", roles.stream().map(r -> r.getId().toString()).collect(Collectors.joining(",")));
                    } else {
                        m.put("currentRoles", "(ninguno)");
                        m.put("currentRoleIds", "");
                    }
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, String>> searchCatalogs(String term) {
        List<Catalog> cats = getCatalogService().findAll();
        String t = term.toLowerCase();
        return cats.stream()
                .filter(c -> t.isEmpty() || c.getName().toLowerCase().contains(t)
                        || c.getCode().toLowerCase().contains(t))
                .map(c -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("_id", c.getId().toString());
                    m.put("_display", c.getCode() + " — " + c.getName() + " [" + c.getType() + "]");
                    m.put("type", c.getType());
                    m.put("code", c.getCode());
                    m.put("name", c.getName());
                    m.put("description", c.getDescription() != null ? c.getDescription() : "");
                    m.put("active", String.valueOf(c.isActive()));
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, String>> searchTenants(String term) {
        List<Tenant> tenants = getTenantRegistryService().findAll();
        String t = term.toLowerCase();
        return tenants.stream()
                .filter(tn -> t.isEmpty() || tn.getName().toLowerCase().contains(t)
                        || tn.getDisplayName().toLowerCase().contains(t))
                .map(tn -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("_id", String.valueOf(tn.getId()));
                    m.put("_display", tn.getName() + " (" + tn.getDisplayName() + ")");
                    m.put("name", tn.getName());
                    m.put("displayName", tn.getDisplayName());
                    m.put("active", String.valueOf(tn.isActive()));
                    m.put("primaryColor", tn.getPrimaryColor() != null ? tn.getPrimaryColor() : "");
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, String>> searchPlatformUsers(String term) {
        List<PlatformUser> pUsers = getPlatformUserService().findAll();
        String t = term.toLowerCase();
        return pUsers.stream()
                .filter(p -> t.isEmpty() || p.getUsername().toLowerCase().contains(t)
                        || (p.getEmail() != null && p.getEmail().toLowerCase().contains(t)))
                .map(p -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("_id", p.getId().toString());
                    m.put("_display", p.getUsername() + (p.getEmail() != null ? " (" + p.getEmail() + ")" : ""));
                    m.put("username", p.getUsername());
                    m.put("email", p.getEmail() != null ? p.getEmail() : "");
                    m.put("fullName", p.getFullName() != null ? p.getFullName() : "");
                    m.put("enabled", String.valueOf(p.isEnabled()));
                    m.put("password", "");
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, String>> searchRolesForPerms(String term) {
        List<Map<String, String>> roleResults = searchRoles(term);
        // Enriquecer con permisos actuales
        for (Map<String, String> r : roleResults) {
            UUID roleId = UUID.fromString(r.get("_id"));
            List<String> schemas = getRoleSchemaService().getSchemaTypesForRole(roleId);
            r.put("currentSchemas", schemas.isEmpty() ? "(ninguno)" : String.join(", ", schemas));
        }
        // Establecer schemas disponibles del tenant
        if (engine.getActiveFlow() != null) {
            Set<String> tenantSchemas = resolveAllTenantSchemaTypes();
            engine.getActiveFlow().getData().put("availableSchemas", String.join(", ", tenantSchemas));
        }
        return roleResults;
    }

    private Set<String> resolveAllTenantSchemaTypes() {
        return getTenantRegistryService().findByName(user.getTenantId())
                .map(tenant -> getTenantSchemaService().findByTenantId(tenant.getId()).stream()
                        .filter(TenantSchema::isActive)
                        .map(TenantSchema::getSchemaType)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElse(new LinkedHashSet<>());
    }

    // ══════════════════════════════════════════════════════════════
    // ── Dispatcher de ejecución ──
    // ══════════════════════════════════════════════════════════════

    private boolean handleExec(String command) {
        switch (command) {
            // Business
            case "EXEC_CREATE_CUSTOMER": return executeCreateCustomer();
            case "EXEC_CREATE_EMPLOYEE": return executeCreateEmployee();
            case "EXEC_START_PROCESS": return executeStartProcess();
            // Roles
            case "EXEC_CREATE_ROLE": return executeCreateRole();
            case "EXEC_UPDATE_ROLE": return executeUpdateRole();
            case "EXEC_DELETE_ROLE": return executeDeleteRole();
            // Users
            case "EXEC_CREATE_USER": return executeCreateUser();
            case "EXEC_UPDATE_USER": return executeUpdateUser();
            case "EXEC_DELETE_USER": return executeDeleteUser();
            // Catalogs
            case "EXEC_CREATE_CATALOG": return executeCreateCatalog();
            case "EXEC_UPDATE_CATALOG": return executeUpdateCatalog();
            case "EXEC_DELETE_CATALOG": return executeDeleteCatalog();
            // Tenants
            case "EXEC_CREATE_TENANT": return executeCreateTenant();
            case "EXEC_UPDATE_TENANT": return executeUpdateTenant();
            // Schema
            case "EXEC_CREATE_SCHEMA": return executeCreateSchema();
            // Platform Users
            case "EXEC_CREATE_PLATFORM_USER": return executeCreatePlatformUser();
            case "EXEC_UPDATE_PLATFORM_USER": return executeUpdatePlatformUser();
            case "EXEC_DELETE_PLATFORM_USER": return executeDeletePlatformUser();
            // Permissions
            case "EXEC_ASSIGN_PERMISSIONS": return executeAssignPermissions();
            default:
                messages.add(new AssistantMessage("Comando no reconocido: " + command, false));
                return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Business ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateCustomer() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            getCustomerService().create(new CreateCustomerRequest(
                    data.get("name"), data.get("email"), data.get("phone"), null));
            messages.add(msg("Cliente \"" + data.get("name") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear cliente: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeCreateEmployee() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            getEmployeeService().create(new CreateEmployeeRequest(
                    data.get("firstName"), data.get("lastName"), data.get("email"),
                    data.get("position"), null, null));

            String email = data.get("email");
            String username = email.substring(0, email.indexOf('@'));
            Set<UUID> roleIds = parseRoleIdsFromData(data);
            String userMsg;
            if (!getUserRepository().existsByUsername(username)) {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setFullName(data.get("firstName") + " " + data.get("lastName"));
                newUser.setEnabled(true);
                getUserService().create(newUser, data.get("password"), roleIds);
                userMsg = " y usuario \"" + username + "\" creados";
            } else {
                userMsg = " creado (usuario \"" + username + "\" ya existía)";
            }
            messages.add(msg("Empleado " + data.get("firstName") + " " + data.get("lastName") + userMsg + " exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear empleado: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeStartProcess() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, Object> vars = new HashMap<>();
            vars.put("orderId", data.get("orderId"));
            if (data.get("customerName") != null) vars.put("customerName", data.get("customerName"));
            vars.put("amount", Double.parseDouble(data.get("amount")));
            if (data.get("description") != null) vars.put("description", data.get("description"));
            vars.put("initiator", user.getUsername());

            String processId = getWorkflowService().startProcess(
                    "sales-approval", user.getTenantId(), data.get("orderId"), vars);
            messages.add(msg("Proceso iniciado (ID: " + processId + ")."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al iniciar proceso: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Roles ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateRole() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Role role = new Role();
            role.setName(data.get("name"));
            role.setDescription(data.get("description"));
            getRoleService().create(role);
            messages.add(msg("Rol \"" + data.get("name") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear rol: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeUpdateRole() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            UUID id = UUID.fromString(sel.get("_id"));
            String name = data.getOrDefault("new_name", sel.get("name"));
            String desc = data.containsKey("new_description") ? data.get("new_description") : sel.get("description");
            getRoleService().update(id, name, desc);
            messages.add(msg("Rol actualizado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al actualizar rol: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeDeleteRole() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            getRoleService().delete(UUID.fromString(sel.get("_id")));
            messages.add(msg("Rol eliminado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al eliminar rol: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Users ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateUser() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Set<UUID> roleIds = parseRoleIdsFromData(data);
            User newUser = new User();
            newUser.setUsername(data.get("username"));
            newUser.setEmail(data.get("email"));
            newUser.setFullName(data.get("fullName"));
            newUser.setEnabled(true);
            getUserService().create(newUser, data.get("password"), roleIds);

            // Auto-crear empleado si no existe
            String email = data.get("email");
            String empMsg = "";
            if (email != null && !email.isBlank()) {
                if (getEmployeeRepository().findByEmail(email).isEmpty()) {
                    String fullName = data.get("fullName").trim();
                    int spaceIdx = fullName.indexOf(' ');
                    String firstName = spaceIdx > 0 ? fullName.substring(0, spaceIdx) : fullName;
                    String lastName = spaceIdx > 0 ? fullName.substring(spaceIdx + 1) : fullName;
                    getEmployeeService().create(new CreateEmployeeRequest(firstName, lastName, email, null, null, null));
                    empMsg = " y empleado";
                }
            }
            messages.add(msg("Usuario \"" + data.get("username") + "\"" + empMsg + " creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear usuario: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeUpdateUser() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            UUID id = UUID.fromString(sel.get("_id"));
            String username = data.getOrDefault("new_username", sel.get("username"));
            String email = data.getOrDefault("new_email", sel.get("email"));
            String fullName = data.getOrDefault("new_fullName", sel.get("fullName"));
            boolean enabled = data.containsKey("new_enabled")
                    ? Boolean.parseBoolean(data.get("new_enabled"))
                    : Boolean.parseBoolean(sel.getOrDefault("enabled", "true"));
            String password = data.getOrDefault("new_password", null);
            if (password != null && password.isBlank()) password = null;
            // Roles: si se modificaron, usar los nuevos; si no, null para preservar existentes
            Set<UUID> roleIds = data.containsKey("new_roles")
                    ? parseRoleIdsFromString(data.get("new_roles"))
                    : null;
            getUserService().update(id, username, email, fullName, enabled, password, roleIds);
            messages.add(msg("Usuario actualizado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al actualizar usuario: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeDeleteUser() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            getUserService().delete(UUID.fromString(sel.get("_id")));
            messages.add(msg("Usuario eliminado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al eliminar usuario: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Catalogs ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateCatalog() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Catalog catalog = new Catalog();
            catalog.setType(data.get("type"));
            catalog.setCode(data.get("code"));
            catalog.setName(data.get("name"));
            catalog.setDescription(data.get("description"));
            catalog.setActive(true);
            catalog.setSortOrder(0);
            getCatalogService().create(catalog, null);
            messages.add(msg("Catálogo \"" + data.get("name") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear catálogo: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeUpdateCatalog() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            UUID id = UUID.fromString(sel.get("_id"));
            String type = data.getOrDefault("new_type", sel.get("type"));
            String code = data.getOrDefault("new_code", sel.get("code"));
            String name = data.getOrDefault("new_name", sel.get("name"));
            String desc = data.containsKey("new_description") ? data.get("new_description") : sel.get("description");
            boolean active = data.containsKey("new_active")
                    ? Boolean.parseBoolean(data.get("new_active"))
                    : Boolean.parseBoolean(sel.getOrDefault("active", "true"));
            getCatalogService().update(id, type, code, name, desc, active, 0, null);
            messages.add(msg("Catálogo actualizado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al actualizar catálogo: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeDeleteCatalog() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            getCatalogService().delete(UUID.fromString(sel.get("_id")));
            messages.add(msg("Catálogo eliminado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al eliminar catálogo: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Tenants ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateTenant() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            getTenantRegistryService().createTenant(data.get("name"), data.get("displayName"));
            messages.add(msg("Tenant \"" + data.get("name") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear tenant: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeUpdateTenant() {
        try {
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            int id = Integer.parseInt(sel.get("_id"));
            String displayName = data.getOrDefault("new_displayName", sel.get("displayName"));
            boolean active = data.containsKey("new_active")
                    ? Boolean.parseBoolean(data.get("new_active"))
                    : Boolean.parseBoolean(sel.getOrDefault("active", "true"));
            String color = data.containsKey("new_primaryColor") ? data.get("new_primaryColor") : sel.get("primaryColor");
            if (color != null && color.isBlank()) color = null;
            getTenantRegistryService().updateTenant(id, displayName, active, color, null);
            messages.add(msg("Tenant actualizado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al actualizar tenant: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Schemas ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreateSchema() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            Tenant tenant = getTenantRegistryService().findByName(user.getTenantId()).orElse(null);
            if (tenant == null) {
                messages.add(msg("Error: tenant actual no encontrado."));
                return false;
            }
            getTenantSchemaService().createSchema(tenant.getId(), data.get("schemaName"), data.get("schemaType"));
            messages.add(msg("Schema \"" + data.get("schemaName") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear schema: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Platform Users ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeCreatePlatformUser() {
        Map<String, String> data = engine.getActiveFlow().getData();
        try {
            getPlatformUserService().create(data.get("username"), data.get("password"),
                    data.get("email"), data.get("fullName"));
            messages.add(msg("Admin de plataforma \"" + data.get("username") + "\" creado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al crear admin plataforma: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeUpdatePlatformUser() {
        try {
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            UUID id = UUID.fromString(sel.get("_id"));
            String email = data.getOrDefault("new_email", sel.get("email"));
            String fullName = data.getOrDefault("new_fullName", sel.get("fullName"));
            boolean enabled = data.containsKey("new_enabled")
                    ? Boolean.parseBoolean(data.get("new_enabled"))
                    : Boolean.parseBoolean(sel.getOrDefault("enabled", "true"));
            getPlatformUserService().update(id, email, fullName, enabled);
            String password = data.getOrDefault("new_password", null);
            if (password != null && !password.isBlank()) {
                getPlatformUserService().changePassword(id, password);
            }
            messages.add(msg("Admin de plataforma actualizado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al actualizar admin plataforma: " + e.getMessage()));
            return false;
        }
    }

    private boolean executeDeletePlatformUser() {
        try {
            Map<String, String> sel = getSelectedRecord();
            getPlatformUserService().delete(UUID.fromString(sel.get("_id")));
            messages.add(msg("Admin de plataforma eliminado exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al eliminar admin plataforma: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Ejecución: Assign Permissions ──
    // ══════════════════════════════════════════════════════════════

    private boolean executeAssignPermissions() {
        try {
            TenantContext.setCurrentTenant(user.getTenantId());
            Map<String, String> sel = getSelectedRecord();
            Map<String, String> data = engine.getActiveFlow().getData();
            UUID roleId = UUID.fromString(sel.get("_id"));
            String newSchemas = data.getOrDefault("newSchemas", "");
            List<String> schemaList = new ArrayList<>();
            for (String s : newSchemas.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) schemaList.add(s);
            }
            getRoleSchemaService().saveAssignments(roleId, schemaList);
            messages.add(msg("Permisos [" + String.join(", ", schemaList) + "] asignados al rol \""
                    + sel.get("name") + "\" exitosamente."));
            return true;
        } catch (Exception e) {
            messages.add(msg("Error al asignar permisos: " + e.getMessage()));
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Helpers ──
    // ══════════════════════════════════════════════════════════════

    private Map<String, String> getSelectedRecord() {
        return engine.getActiveFlow().getSearchResults()
                .get(engine.getActiveFlow().getSelectedResultIndex());
    }

    private AssistantMessage msg(String text) {
        return new AssistantMessage(text, false);
    }

    private Set<UUID> parseRoleIdsFromData(Map<String, String> data) {
        String roleIds = data.getOrDefault("roleIds", "");
        return parseRoleIdsFromString(roleIds);
    }

    private Set<UUID> parseRoleIdsFromString(String commaIds) {
        Set<UUID> result = new LinkedHashSet<>();
        if (commaIds == null || commaIds.isBlank()) return result;
        for (String id : commaIds.split(",")) {
            id = id.trim();
            if (!id.isEmpty()) {
                try { result.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private void postNavigate(String page) {
        Map<String, Object> args = new HashMap<>();
        args.put("page", page);
        BindUtils.postGlobalCommand(null, null, "navigateTo", args);
    }

    private void postNavigateForceReload(String page) {
        Map<String, Object> args = new HashMap<>();
        args.put("page", page);
        BindUtils.postGlobalCommand(null, null, "navigateForceReload", args);
    }

    private Set<String> resolveAllowedSchemas() {
        if (user == null || user.getTenantId() == null) return Set.of();

        TenantRegistryService trs = SpringUtil.getApplicationContext().getBean(TenantRegistryService.class);
        TenantSchemaService tss = SpringUtil.getApplicationContext().getBean(TenantSchemaService.class);

        Set<String> tenantSchemaTypes = trs.findByName(user.getTenantId())
                .map(tenant -> tss.findByTenantId(tenant.getId()).stream()
                        .filter(TenantSchema::isActive)
                        .map(TenantSchema::getSchemaType)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        if (user.isPlatformAdmin() || user.hasRole(RoleConstants.ADMIN)) {
            return tenantSchemaTypes;
        }

        RoleSchemaService rss = SpringUtil.getApplicationContext().getBean(RoleSchemaService.class);
        Set<String> roleSchemaTypes = rss.getSchemaTypesForRoles(user.getRoles());
        return roleSchemaTypes.stream()
                .filter(tenantSchemaTypes::contains)
                .collect(Collectors.toSet());
    }

    private void scrollToBottom() {
        Clients.evalJavaScript(
                "setTimeout(function(){var c=jq('.assistant-messages')[0];if(c)c.scrollTop=c.scrollHeight;},100)");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lazy service getters ──
    // ══════════════════════════════════════════════════════════════

    private CustomerService getCustomerService() {
        if (customerService == null) customerService = SpringUtil.getApplicationContext().getBean(CustomerService.class);
        return customerService;
    }
    private EmployeeService getEmployeeService() {
        if (employeeService == null) employeeService = SpringUtil.getApplicationContext().getBean(EmployeeService.class);
        return employeeService;
    }
    private EmployeeRepository getEmployeeRepository() {
        if (employeeRepository == null) employeeRepository = SpringUtil.getApplicationContext().getBean(EmployeeRepository.class);
        return employeeRepository;
    }
    private WorkflowService getWorkflowService() {
        if (workflowService == null) workflowService = SpringUtil.getApplicationContext().getBean(WorkflowService.class);
        return workflowService;
    }
    private UserService getUserService() {
        if (userService == null) userService = SpringUtil.getApplicationContext().getBean(UserService.class);
        return userService;
    }
    private UserRepository getUserRepository() {
        if (userRepository == null) userRepository = SpringUtil.getApplicationContext().getBean(UserRepository.class);
        return userRepository;
    }
    private RoleService getRoleService() {
        if (roleService == null) roleService = SpringUtil.getApplicationContext().getBean(RoleService.class);
        return roleService;
    }
    private CatalogService getCatalogService() {
        if (catalogService == null) catalogService = SpringUtil.getApplicationContext().getBean(CatalogService.class);
        return catalogService;
    }
    private TenantRegistryService getTenantRegistryService() {
        if (tenantRegistryService == null) tenantRegistryService = SpringUtil.getApplicationContext().getBean(TenantRegistryService.class);
        return tenantRegistryService;
    }
    private TenantSchemaService getTenantSchemaService() {
        if (tenantSchemaService == null) tenantSchemaService = SpringUtil.getApplicationContext().getBean(TenantSchemaService.class);
        return tenantSchemaService;
    }
    private PlatformUserService getPlatformUserService() {
        if (platformUserService == null) platformUserService = SpringUtil.getApplicationContext().getBean(PlatformUserService.class);
        return platformUserService;
    }
    private RoleSchemaService getRoleSchemaService() {
        if (roleSchemaService == null) roleSchemaService = SpringUtil.getApplicationContext().getBean(RoleSchemaService.class);
        return roleSchemaService;
    }

    // ══════════════════════════════════════════════════════════════
    // ── Getters / Setters (ZK binding) ──
    // ══════════════════════════════════════════════════════════════

    public List<AssistantMessage> getMessages() { return messages; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
}
