package com.lreyes.platform.ui.zk.service;

import java.util.*;

/**
 * Motor de reglas del asistente virtual.
 * Soporta navegación, creación, edición, eliminación y asignación de permisos.
 */
public class AssistantEngine {

    // ══════════════════════════════════════════════════════════════
    // ── Clases internas ──
    // ══════════════════════════════════════════════════════════════

    public static class Response {
        private final String text;
        private final String navigateTo;
        private final String navigateAfterExec;

        public Response(String text) { this(text, null, null); }
        public Response(String text, String navigateTo) { this(text, navigateTo, null); }
        public Response(String text, String navigateTo, String navigateAfterExec) {
            this.text = text; this.navigateTo = navigateTo; this.navigateAfterExec = navigateAfterExec;
        }
        public String getText() { return text; }
        public String getNavigateTo() { return navigateTo; }
        public String getNavigateAfterExec() { return navigateAfterExec; }
    }

    public enum FlowType {
        // Business
        CREATE_CUSTOMER, CREATE_EMPLOYEE, EDIT_EMPLOYEE, DELETE_EMPLOYEE, START_PROCESS,
        // Admin – Role
        CREATE_ROLE, EDIT_ROLE, DELETE_ROLE,
        // Admin – User
        CREATE_USER, EDIT_USER, DELETE_USER,
        // Admin – Catalog
        CREATE_CATALOG, EDIT_CATALOG, DELETE_CATALOG,
        // Admin – Permissions
        ASSIGN_PERMISSIONS,
        // Platform – Tenant
        CREATE_TENANT, EDIT_TENANT,
        // Platform – Schema
        CREATE_SCHEMA,
        // Platform – Platform User
        CREATE_PLATFORM_USER, EDIT_PLATFORM_USER, DELETE_PLATFORM_USER
    }

    public static class FlowState {
        private final FlowType type;
        private int step;
        private final Map<String, String> data = new LinkedHashMap<>();
        private List<Map<String, String>> searchResults;
        private int selectedResultIndex = -1;
        private List<Map<String, String>> availableRoles;

        public FlowState(FlowType type) { this.type = type; }
        public FlowType getType() { return type; }
        public int getStep() { return step; }
        public void setStep(int s) { step = s; }
        public void nextStep() { step++; }
        public Map<String, String> getData() { return data; }
        public List<Map<String, String>> getSearchResults() { return searchResults; }
        public void setSearchResults(List<Map<String, String>> r) { searchResults = r; }
        public int getSelectedResultIndex() { return selectedResultIndex; }
        public void setSelectedResultIndex(int i) { selectedResultIndex = i; }
        public List<Map<String, String>> getAvailableRoles() { return availableRoles; }
        public void setAvailableRoles(List<Map<String, String>> r) { availableRoles = r; }
    }

    private static class NavTarget {
        final String page, label, access;
        final List<String> keywords;
        NavTarget(String page, String label, String access, String... kw) {
            this.page = page; this.label = label; this.access = access; this.keywords = List.of(kw);
        }
    }

    private static class CrudAction {
        final FlowType flowType;
        final String access;
        final String entityLabel;
        final List<String> keywords;
        CrudAction(FlowType ft, String access, String label, String... kw) {
            this.flowType = ft; this.access = access; this.entityLabel = label; this.keywords = List.of(kw);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Datos estáticos ──
    // ══════════════════════════════════════════════════════════════

    private static final List<NavTarget> NAV_TARGETS = List.of(
        new NavTarget("~./zul/dashboard.zul", "Panel principal (Dashboard)", "all",
                "dashboard", "inicio", "panel", "escritorio", "principal"),
        new NavTarget("~./zul/customers/list.zul", "Clientes", "sales",
                "clientes", "cliente"),
        new NavTarget("~./zul/employees/list.zul", "Empleados", "hr",
                "empleados", "empleado"),
        new NavTarget("~./zul/workflow/tasks.zul", "Procesos / Tareas", "sales",
                "tareas", "procesos", "flujos", "workflow"),
        new NavTarget("~./zul/admin/roles.zul", "Roles", "admin",
                "roles", "rol"),
        new NavTarget("~./zul/admin/users.zul", "Usuarios", "admin",
                "usuarios", "usuario"),
        new NavTarget("~./zul/admin/catalogs.zul", "Catálogos", "admin",
                "catalogos", "catalogo"),
        new NavTarget("~./zul/admin/role-schemas.zul", "Permisos", "admin",
                "permisos", "permiso"),
        new NavTarget("~./zul/platform/tenants.zul", "Tenants", "platform_admin",
                "tenants", "tenant", "inquilinos"),
        new NavTarget("~./zul/platform/schemas.zul", "Schemas", "platform_admin",
                "schemas", "schema", "esquemas"),
        new NavTarget("~./zul/platform/platform-users.zul", "Admins Plataforma", "platform_admin",
                "admins plataforma", "administradores", "platform admins")
    );

    private static final List<String> NAV_PREFIXES = List.of(
        "ir a ", "ir al ", "navegar a ", "navegar al ", "abrir ", "ver ", "mostrar ",
        "llevar a ", "llevame a ", "quiero ver ", "quiero ir a ",
        "abre ", "muestra ", "muestrame ", "lista de ", "lista ");

    // ── Acciones CRUD ──

    private static final List<CrudAction> CREATE_ACTIONS = List.of(
        new CrudAction(FlowType.CREATE_CUSTOMER, "sales", "cliente", "cliente", "clientes"),
        new CrudAction(FlowType.CREATE_EMPLOYEE, "hr", "empleado", "empleado", "empleados"),
        new CrudAction(FlowType.START_PROCESS, "sales", "proceso", "proceso", "venta", "procesos"),
        new CrudAction(FlowType.CREATE_ROLE, "admin", "rol", "rol", "roles"),
        new CrudAction(FlowType.CREATE_USER, "admin", "usuario", "usuario", "usuarios"),
        new CrudAction(FlowType.CREATE_CATALOG, "admin", "catálogo", "catalogo", "catalogos"),
        new CrudAction(FlowType.CREATE_TENANT, "platform_admin", "tenant", "tenant", "tenants"),
        new CrudAction(FlowType.CREATE_SCHEMA, "platform_admin", "schema", "schema", "schemas", "esquema"),
        new CrudAction(FlowType.CREATE_PLATFORM_USER, "platform_admin", "admin plataforma",
                "admin plataforma", "platform admin", "administrador plataforma")
    );

    private static final List<CrudAction> EDIT_ACTIONS = List.of(
        new CrudAction(FlowType.EDIT_EMPLOYEE, "hr", "empleado", "empleado", "empleados"),
        new CrudAction(FlowType.EDIT_ROLE, "admin", "rol", "rol", "roles"),
        new CrudAction(FlowType.EDIT_USER, "admin", "usuario", "usuario", "usuarios"),
        new CrudAction(FlowType.EDIT_CATALOG, "admin", "catálogo", "catalogo", "catalogos"),
        new CrudAction(FlowType.EDIT_TENANT, "platform_admin", "tenant", "tenant", "tenants"),
        new CrudAction(FlowType.EDIT_PLATFORM_USER, "platform_admin", "admin plataforma",
                "admin plataforma", "platform admin", "administrador plataforma")
    );

    private static final List<CrudAction> DELETE_ACTIONS = List.of(
        new CrudAction(FlowType.DELETE_EMPLOYEE, "hr", "empleado", "empleado", "empleados"),
        new CrudAction(FlowType.DELETE_ROLE, "admin", "rol", "rol", "roles"),
        new CrudAction(FlowType.DELETE_USER, "admin", "usuario", "usuario", "usuarios"),
        new CrudAction(FlowType.DELETE_CATALOG, "admin", "catálogo", "catalogo", "catalogos"),
        new CrudAction(FlowType.DELETE_PLATFORM_USER, "platform_admin", "admin plataforma",
                "admin plataforma", "platform admin", "administrador plataforma")
    );

    private static final List<String> CREATE_PREFIXES = List.of(
        "crear ", "nuevo ", "nueva ", "agregar ", "registrar ", "iniciar ", "comenzar ");
    private static final List<String> EDIT_PREFIXES = List.of(
        "editar ", "modificar ", "cambiar ", "actualizar ");
    private static final List<String> DELETE_PREFIXES = List.of(
        "eliminar ", "borrar ", "quitar ", "remover ");
    private static final List<String> ASSIGN_KEYWORDS = List.of(
        "asignar permisos", "gestionar permisos", "permisos de rol",
        "cambiar permisos", "asignar permiso", "configurar permisos");

    private static final List<String> GREET_KEYWORDS = List.of(
        "hola", "buenos dias", "buenas tardes", "buenas noches", "hey", "saludos", "hi", "hello", "buen dia");
    private static final List<String> HELP_KEYWORDS = List.of(
        "ayuda", "help", "que puedes hacer", "opciones", "comandos", "menu", "que haces");

    // ── Definición de campos editables por entidad ──

    private static final String[][] ROLE_FIELDS = {
        {"name", "Nombre"}, {"description", "Descripción"}
    };
    private static final String[][] USER_FIELDS = {
        {"username", "Username"}, {"email", "Email"}, {"fullName", "Nombre completo"},
        {"enabled", "Activo (sí/no)"}, {"password", "Contraseña (vacío=no cambiar)"},
        {"roles", "Roles"}
    };
    private static final String[][] CATALOG_FIELDS = {
        {"type", "Tipo"}, {"code", "Código"}, {"name", "Nombre"},
        {"description", "Descripción"}, {"active", "Activo (sí/no)"}
    };
    private static final String[][] TENANT_FIELDS = {
        {"displayName", "Nombre visible"}, {"active", "Activo (sí/no)"}, {"primaryColor", "Color (#hex)"}
    };
    private static final String[][] PLATFORM_USER_FIELDS = {
        {"email", "Email"}, {"fullName", "Nombre completo"},
        {"enabled", "Activo (sí/no)"}, {"password", "Contraseña (vacío=no cambiar)"}
    };
    private static final String[][] EMPLOYEE_FIELDS = {
        {"firstName", "Nombre"}, {"lastName", "Apellido"}, {"email", "Email"},
        {"position", "Cargo"}, {"department", "Departamento"}, {"active", "Activo (sí/no)"}
    };

    // ══════════════════════════════════════════════════════════════
    // ── Estado ──
    // ══════════════════════════════════════════════════════════════

    private FlowState activeFlow;
    public FlowState getActiveFlow() { return activeFlow; }
    public void clearFlow() { activeFlow = null; }

    // ══════════════════════════════════════════════════════════════
    // ── Proceso principal ──
    // ══════════════════════════════════════════════════════════════

    public List<Response> process(String input, Set<String> allowedSchemas,
                                   boolean isAdmin, boolean isPlatformAdmin) {
        if (input == null || input.isBlank()) {
            return List.of(new Response("Escribe algo para que pueda ayudarte."));
        }

        String normalized = normalize(input.trim());

        // Flujo activo
        if (activeFlow != null) {
            if (normalized.equals("cancelar") || normalized.equals("salir")) {
                activeFlow = null;
                return List.of(new Response("Operación cancelada. ¿En qué más puedo ayudarte?"));
            }
            return processFlowStep(normalized, input.trim());
        }

        // 1. Saludo
        for (String kw : GREET_KEYWORDS) {
            if (normalized.contains(kw))
                return List.of(new Response(buildGreeting(allowedSchemas, isAdmin, isPlatformAdmin)));
        }

        // 2. Ayuda
        for (String kw : HELP_KEYWORDS) {
            if (normalized.contains(kw))
                return List.of(new Response(buildHelp(allowedSchemas, isAdmin, isPlatformAdmin)));
        }

        // 3. Asignar permisos (antes de create/edit para evitar conflicto)
        for (String kw : ASSIGN_KEYWORDS) {
            if (normalized.contains(kw)) {
                if (!hasAccess("admin", allowedSchemas, isAdmin, isPlatformAdmin))
                    return List.of(new Response("No tienes acceso a esta función."));
                return startSearchFlow(FlowType.ASSIGN_PERMISSIONS, "rol", "");
            }
        }

        // 4. Crear
        CrudMatch createMatch = matchCrud(normalized, CREATE_ACTIONS, CREATE_PREFIXES);
        if (createMatch != null) {
            if (!hasAccess(createMatch.action.access, allowedSchemas, isAdmin, isPlatformAdmin))
                return List.of(new Response("No tienes acceso a esta función. Escribe \"ayuda\" para ver tus opciones."));
            return startCreateFlow(createMatch.action.flowType);
        }

        // 5. Editar
        CrudMatch editMatch = matchCrud(normalized, EDIT_ACTIONS, EDIT_PREFIXES);
        if (editMatch != null) {
            if (!hasAccess(editMatch.action.access, allowedSchemas, isAdmin, isPlatformAdmin))
                return List.of(new Response("No tienes acceso a esta función."));
            return startSearchFlow(editMatch.action.flowType, editMatch.action.entityLabel, editMatch.searchTerm);
        }

        // 6. Eliminar
        CrudMatch deleteMatch = matchCrud(normalized, DELETE_ACTIONS, DELETE_PREFIXES);
        if (deleteMatch != null) {
            if (!hasAccess(deleteMatch.action.access, allowedSchemas, isAdmin, isPlatformAdmin))
                return List.of(new Response("No tienes acceso a esta función."));
            return startSearchFlow(deleteMatch.action.flowType, deleteMatch.action.entityLabel, deleteMatch.searchTerm);
        }

        // 7. Navegación
        NavTarget navMatch = matchNavigation(normalized);
        if (navMatch != null) {
            if (!hasAccess(navMatch.access, allowedSchemas, isAdmin, isPlatformAdmin))
                return List.of(new Response("No tienes acceso a esta vista."));
            return List.of(new Response("Listo, te llevo a " + navMatch.label + ".", navMatch.page));
        }

        // 8. No entendí
        return List.of(new Response(
            "No entendí tu mensaje. Prueba con frases como:\n"
            + "  \"ir a clientes\", \"crear rol\", \"editar usuario admin\"\n"
            + "  \"eliminar catálogo\", \"asignar permisos\"\n"
            + "\nEscribe \"ayuda\" para ver todos los comandos."));
    }

    /**
     * Callback del VM después de ejecutar una búsqueda (EXEC_SEARCH).
     */
    public List<Response> continueWithSearchResults(List<Map<String, String>> results) {
        if (activeFlow == null)
            return List.of(new Response("Error interno."));

        activeFlow.setSearchResults(results);

        if (results.isEmpty()) {
            activeFlow.setStep(0);
            return List.of(new Response("No encontré resultados. Intenta con otro nombre, o \"cancelar\":"));
        }

        if (results.size() == 1) {
            activeFlow.setSelectedResultIndex(0);
            activeFlow.setStep(100);
            return buildPostSelectionResponse();
        }

        // Múltiples resultados
        activeFlow.setStep(50);
        StringBuilder sb = new StringBuilder("Encontré " + results.size() + " resultados:\n");
        for (int i = 0; i < Math.min(results.size(), 10); i++) {
            sb.append("  ").append(i + 1).append(". ").append(results.get(i).get("_display")).append("\n");
        }
        if (results.size() > 10) sb.append("  (mostrando los primeros 10)\n");
        sb.append("\nEscribe el número:");
        return List.of(new Response(sb.toString()));
    }

    /**
     * Callback del VM después de cargar roles disponibles (LOAD_ROLES).
     */
    public List<Response> continueWithRoles(List<Map<String, String>> roles) {
        if (activeFlow == null)
            return List.of(new Response("Error interno."));

        activeFlow.setAvailableRoles(roles);

        if (roles == null || roles.isEmpty()) {
            // No hay roles, skip
            activeFlow.getData().put("roleIds", "");
            activeFlow.getData().put("roleNames", "(ninguno)");
            activeFlow.nextStep(); // avanzar al confirm
            return buildRoleSkipResponse();
        }

        // Construir lista numerada
        StringBuilder sb = new StringBuilder("Roles disponibles:\n");
        // En modo edit, marcar los asignados actualmente
        String currentRoleIds = activeFlow.getData().getOrDefault("currentRoleIds", "");
        Set<String> currentSet = new LinkedHashSet<>();
        if (!currentRoleIds.isEmpty()) {
            for (String id : currentRoleIds.split(",")) {
                id = id.trim();
                if (!id.isEmpty()) currentSet.add(id);
            }
        }
        for (int i = 0; i < roles.size(); i++) {
            String marker = currentSet.contains(roles.get(i).get("_id")) ? " *" : "";
            sb.append("  ").append(i + 1).append(". ").append(roles.get(i).get("name")).append(marker).append("\n");
        }
        if (!currentSet.isEmpty()) sb.append("  (* = asignado actualmente)\n");
        sb.append("\nEscribe los números separados por coma (ej: 1,3), o \"-\" para ninguno:");
        return ask(sb.toString());
    }

    /**
     * Parsea la selección de roles del usuario (ej: "1,3") contra availableRoles.
     */
    public List<Response> parseRoleSelection(String input) {
        if (activeFlow == null || activeFlow.getAvailableRoles() == null)
            return ask("Error interno.");

        List<Map<String, String>> roles = activeFlow.getAvailableRoles();
        String normalized = input.trim().toLowerCase();

        if (normalized.equals("-") || normalized.equals("ninguno") || normalized.equals("none")) {
            activeFlow.getData().put("roleIds", "");
            activeFlow.getData().put("roleNames", "(ninguno)");
            activeFlow.nextStep();
            return null; // señal de éxito, caller maneja siguiente paso
        }

        String[] parts = input.split("[,;\\s]+");
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                int idx = Integer.parseInt(part) - 1;
                if (idx >= 0 && idx < roles.size()) {
                    String id = roles.get(idx).get("_id");
                    if (!ids.contains(id)) {
                        ids.add(id);
                        names.add(roles.get(idx).get("name"));
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        if (ids.isEmpty()) {
            return ask("No seleccionaste roles válidos. Escribe números de la lista (ej: 1,3), o \"-\" para ninguno:");
        }

        activeFlow.getData().put("roleIds", String.join(",", ids));
        activeFlow.getData().put("roleNames", String.join(", ", names));
        activeFlow.nextStep();
        return null; // éxito
    }

    private List<Response> buildRoleSkipResponse() {
        FlowType type = activeFlow.getType();
        Map<String, String> d = activeFlow.getData();
        // Edit: no hay roles, volver al menú de edición
        if (isEditFlow()) {
            activeFlow.setStep(100);
            return ask("No hay roles disponibles.\n" + buildEditMenu()
                + "\nCampo (número), \"guardar\" o \"cancelar\":");
        }
        if (type == FlowType.CREATE_USER) {
            String roles = d.getOrDefault("roleNames", "(ninguno)");
            return ask(buildSummary("Nuevo usuario", "Username", d.get("username"), "Email", d.get("email"),
                "Nombre", d.get("fullName"), "Roles", roles)
                + "\nSe creará empleado automáticamente.\n\n¿Confirmar? (sí/no)");
        }
        if (type == FlowType.CREATE_EMPLOYEE) {
            String roles = d.getOrDefault("roleNames", "(ninguno)");
            return ask(buildSummary("Nuevo empleado", "Nombre", d.get("firstName") + " " + d.get("lastName"),
                "Email", d.get("email"), "Cargo", d.get("position"), "Roles", roles)
                + "\nSe creará usuario automáticamente.\n\n¿Confirmar? (sí/no)");
        }
        return ask("¿Confirmar? (sí/no)");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Matching ──
    // ══════════════════════════════════════════════════════════════

    private static class CrudMatch {
        final CrudAction action;
        final String searchTerm;
        CrudMatch(CrudAction a, String t) { action = a; searchTerm = t; }
    }

    private CrudMatch matchCrud(String normalized, List<CrudAction> actions, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                String rest = normalized.substring(prefix.length()).trim();
                // Priorizar keywords más largos
                CrudAction best = null;
                int bestLen = 0;
                String bestTerm = "";
                for (CrudAction action : actions) {
                    for (String kw : action.keywords) {
                        if (kw.length() > bestLen && (rest.equals(kw) || rest.startsWith(kw + " ") || rest.startsWith(kw))) {
                            best = action;
                            bestLen = kw.length();
                            bestTerm = rest.length() > kw.length() ? rest.substring(kw.length()).trim() : "";
                        }
                    }
                }
                if (best != null) return new CrudMatch(best, bestTerm);
            }
        }
        // Frases exactas adicionales para proceso
        if (normalized.contains("nueva venta") || normalized.contains("iniciar venta") || normalized.contains("aprobar venta")) {
            for (CrudAction a : CREATE_ACTIONS) {
                if (a.flowType == FlowType.START_PROCESS) return new CrudMatch(a, "");
            }
        }
        return null;
    }

    private NavTarget matchNavigation(String normalized) {
        for (String prefix : NAV_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                String rest = normalized.substring(prefix.length()).trim();
                NavTarget match = findNavByKeyword(rest);
                if (match != null) return match;
            }
        }
        NavTarget best = null;
        int bestLen = 0;
        for (NavTarget target : NAV_TARGETS) {
            for (String kw : target.keywords) {
                if (kw.length() > bestLen && normalized.contains(kw)) {
                    best = target;
                    bestLen = kw.length();
                }
            }
        }
        return best;
    }

    private NavTarget findNavByKeyword(String text) {
        for (NavTarget target : NAV_TARGETS)
            for (String kw : target.keywords)
                if (text.equals(kw) || text.startsWith(kw)) return target;
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // ── Inicio de flujos ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> startCreateFlow(FlowType type) {
        activeFlow = new FlowState(type);
        switch (type) {
            case CREATE_CUSTOMER:
                return ask("Vamos a crear un nuevo cliente.\n¿Nombre del cliente? (obligatorio)");
            case CREATE_EMPLOYEE:
                return ask("Vamos a crear un nuevo empleado.\n¿Nombre (primer nombre)? (obligatorio)");
            case START_PROCESS:
                return ask("Vamos a iniciar un proceso de venta.\n¿ID del pedido? (obligatorio)");
            case CREATE_ROLE:
                return ask("Vamos a crear un nuevo rol.\n¿Nombre del rol? (obligatorio)");
            case CREATE_USER:
                return ask("Vamos a crear un nuevo usuario.\n¿Username? (obligatorio)");
            case CREATE_CATALOG:
                return ask("Vamos a crear un catálogo.\n¿Tipo del catálogo? (obligatorio, ej: PAIS, MONEDA)");
            case CREATE_TENANT:
                return ask("Vamos a crear un nuevo tenant.\n¿Nombre identificador? (obligatorio, sin espacios, ej: miempresa)");
            case CREATE_SCHEMA:
                return ask("Vamos a crear un schema para el tenant actual.\n¿Nombre del schema? (obligatorio, sin espacios)");
            case CREATE_PLATFORM_USER:
                return ask("Vamos a crear un admin de plataforma.\n¿Username? (obligatorio)");
            default:
                activeFlow = null;
                return ask("Error interno.");
        }
    }

    private List<Response> startSearchFlow(FlowType type, String entityLabel, String searchTerm) {
        activeFlow = new FlowState(type);
        if (searchTerm != null && !searchTerm.isBlank()) {
            activeFlow.getData().put("searchTerm", searchTerm);
            return List.of(new Response("EXEC_SEARCH:" + getEntityCode()));
        }
        String action = isDeleteFlow() ? "eliminar" : isEditFlow() ? "editar" : "gestionar permisos de";
        return ask("¿Nombre del " + entityLabel + " a " + action + "?");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Dispatch de flujo activo ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> processFlowStep(String normalized, String raw) {
        FlowType type = activeFlow.getType();

        // Pasos comunes de búsqueda/selección para flujos que lo requieren
        if (isSearchableFlow()) {
            List<Response> r = processSearchStep(normalized, raw);
            if (r != null) return r;
        }

        switch (type) {
            // ── Business creates ──
            case CREATE_CUSTOMER: return processCreateCustomer(normalized, raw);
            case CREATE_EMPLOYEE: return processCreateEmployee(normalized, raw);
            case START_PROCESS: return processStartProcess(normalized, raw);
            // ── Admin creates ──
            case CREATE_ROLE: return processCreateRole(normalized, raw);
            case CREATE_USER: return processCreateUser(normalized, raw);
            case CREATE_CATALOG: return processCreateCatalog(normalized, raw);
            // ── Platform creates ──
            case CREATE_TENANT: return processCreateTenant(normalized, raw);
            case CREATE_SCHEMA: return processCreateSchema(normalized, raw);
            case CREATE_PLATFORM_USER: return processCreatePlatformUser(normalized, raw);
            // ── Edits (genérico) ──
            case EDIT_EMPLOYEE:
            case EDIT_ROLE: case EDIT_USER: case EDIT_CATALOG:
            case EDIT_TENANT: case EDIT_PLATFORM_USER:
                return processEditGeneric(normalized, raw);
            // ── Deletes (genérico) ──
            case DELETE_EMPLOYEE:
            case DELETE_ROLE: case DELETE_USER: case DELETE_CATALOG:
            case DELETE_PLATFORM_USER:
                return processDeleteGeneric(normalized);
            // ── Permisos ──
            case ASSIGN_PERMISSIONS:
                return processAssignPermissions(normalized, raw);
            default:
                activeFlow = null;
                return ask("Error interno.");
        }
    }

    /** Maneja steps 0 (colectar término) y 50 (seleccionar de lista). */
    private List<Response> processSearchStep(String normalized, String raw) {
        int step = activeFlow.getStep();
        if (step == 0) {
            activeFlow.getData().put("searchTerm", raw);
            return List.of(new Response("EXEC_SEARCH:" + getEntityCode()));
        }
        if (step == 50) {
            try {
                int idx = Integer.parseInt(normalized.trim()) - 1;
                if (idx >= 0 && activeFlow.getSearchResults() != null
                        && idx < activeFlow.getSearchResults().size()) {
                    activeFlow.setSelectedResultIndex(idx);
                    activeFlow.setStep(100);
                    return buildPostSelectionResponse();
                }
            } catch (NumberFormatException ignored) {}
            return ask("Escribe un número válido de la lista, o \"cancelar\":");
        }
        return null; // No es step de búsqueda, delegar al handler específico
    }

    /** Construye la respuesta después de seleccionar un registro. */
    private List<Response> buildPostSelectionResponse() {
        Map<String, String> sel = activeFlow.getSearchResults().get(activeFlow.getSelectedResultIndex());

        if (isDeleteFlow()) {
            return ask("¿Eliminar \"" + sel.get("_display") + "\"? (sí/no)");
        }

        if (isEditFlow()) {
            return ask("Editando \"" + sel.get("_display") + "\":\n" + buildEditMenu()
                + "\nCampo a cambiar (número), \"guardar\" o \"cancelar\":");
        }

        if (activeFlow.getType() == FlowType.ASSIGN_PERMISSIONS) {
            String current = sel.getOrDefault("currentSchemas", "(ninguno)");
            String available = activeFlow.getData().getOrDefault("availableSchemas", "");
            return ask("Rol: " + sel.get("name")
                + "\nPermisos actuales: " + current
                + "\nDisponibles: " + available
                + "\n\nEscribe los permisos a asignar (separados por coma), o \"cancelar\":");
        }

        return ask("Error interno.");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Flujos de CREACIÓN ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> processCreateCustomer(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("name", raw); activeFlow.nextStep();
                return ask("Nombre: " + raw + "\n¿Email? (opcional, \"-\" para omitir)");
            case 1:
                d.put("email", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask("¿Teléfono? (opcional, \"-\" para omitir)");
            case 2:
                d.put("phone", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo cliente", "Nombre", d.get("name"), "Email", d.get("email"), "Teléfono", d.get("phone"))
                    + "\n\n¿Confirmar? (sí/no)");
            case 3:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_CUSTOMER", null, "~./zul/customers/list.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateEmployee(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("firstName", raw); activeFlow.nextStep();
                return ask("¿Apellido? (obligatorio)");
            case 1:
                if (raw.isBlank()) return ask("El apellido es obligatorio:");
                d.put("lastName", raw); activeFlow.nextStep();
                return ask("¿Email? (obligatorio, válido)");
            case 2:
                if (raw.isBlank() || !raw.matches(".+@.+\\..+")) return ask("Email válido (ej: nombre@empresa.com):");
                d.put("email", raw); activeFlow.nextStep();
                return ask("¿Cargo? (opcional, \"-\" para omitir)");
            case 3:
                d.put("position", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask("¿Contraseña para el nuevo usuario? (obligatoria)");
            case 4:
                if (raw.isBlank()) return ask("La contraseña es obligatoria:");
                d.put("password", raw); activeFlow.nextStep();
                // step ahora es 5, LOAD_ROLES trigger
                return List.of(new Response("LOAD_ROLES"));
            case 5: {
                // continueWithRoles() dejó step en 5; usuario responde aquí
                List<Response> err = parseRoleSelection(raw);
                if (err != null) return err;
                // parseRoleSelection hizo nextStep → step=6 = confirm
                String roles = d.getOrDefault("roleNames", "(ninguno)");
                return ask(buildSummary("Nuevo empleado", "Nombre", d.get("firstName") + " " + d.get("lastName"),
                    "Email", d.get("email"), "Cargo", d.get("position"), "Roles", roles)
                    + "\nSe creará usuario automáticamente.\n\n¿Confirmar? (sí/no)");
            }
            case 6:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_EMPLOYEE", null, "~./zul/employees/list.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processStartProcess(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El ID del pedido es obligatorio:");
                d.put("orderId", raw); activeFlow.nextStep();
                return ask("¿Nombre del cliente? (opcional, \"-\" para omitir)");
            case 1:
                d.put("customerName", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask("¿Monto de la venta? (obligatorio, > 0)");
            case 2:
                try { double a = Double.parseDouble(raw.replace(",", "."));
                    if (a <= 0) return ask("Debe ser > 0:");
                    d.put("amount", String.valueOf(a));
                } catch (NumberFormatException e) { return ask("Número válido:"); }
                activeFlow.nextStep();
                return ask("¿Descripción? (opcional, \"-\" para omitir)");
            case 3:
                d.put("description", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo proceso", "Pedido", d.get("orderId"), "Cliente", d.get("customerName"),
                    "Monto", d.get("amount"), "Descripción", d.get("description")) + "\n\n¿Confirmar? (sí/no)");
            case 4:
                if (isConfirm(n)) return List.of(new Response("EXEC_START_PROCESS", null, "~./zul/workflow/tasks.zul"));
                activeFlow = null; return ask("Proceso cancelado.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateRole(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("name", raw); activeFlow.nextStep();
                return ask("¿Descripción? (opcional, \"-\" para omitir)");
            case 1:
                d.put("description", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo rol", "Nombre", d.get("name"), "Descripción", d.get("description"))
                    + "\n\n¿Confirmar? (sí/no)");
            case 2:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_ROLE", null, "~./zul/admin/roles.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateUser(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El username es obligatorio:");
                d.put("username", raw); activeFlow.nextStep();
                return ask("¿Email? (obligatorio)");
            case 1:
                if (raw.isBlank() || !raw.matches(".+@.+\\..+")) return ask("Email válido:");
                d.put("email", raw); activeFlow.nextStep();
                return ask("¿Nombre completo? (obligatorio)");
            case 2:
                if (raw.isBlank()) return ask("El nombre completo es obligatorio:");
                d.put("fullName", raw); activeFlow.nextStep();
                return ask("¿Contraseña? (obligatoria)");
            case 3:
                if (raw.isBlank()) return ask("La contraseña es obligatoria:");
                d.put("password", raw); activeFlow.nextStep();
                // step ahora es 4, LOAD_ROLES trigger
                return List.of(new Response("LOAD_ROLES"));
            case 4: {
                // continueWithRoles() dejó step en 4; usuario responde aquí
                List<Response> err = parseRoleSelection(raw);
                if (err != null) return err;
                // parseRoleSelection hizo nextStep → step=5 = confirm
                String roles = d.getOrDefault("roleNames", "(ninguno)");
                return ask(buildSummary("Nuevo usuario", "Username", d.get("username"), "Email", d.get("email"),
                    "Nombre", d.get("fullName"), "Roles", roles)
                    + "\nSe creará empleado automáticamente.\n\n¿Confirmar? (sí/no)");
            }
            case 5:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_USER", null, "~./zul/admin/users.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateCatalog(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El tipo es obligatorio:");
                d.put("type", raw.toUpperCase()); activeFlow.nextStep();
                return ask("¿Código? (obligatorio, ej: MXN, US)");
            case 1:
                if (raw.isBlank()) return ask("El código es obligatorio:");
                d.put("code", raw); activeFlow.nextStep();
                return ask("¿Nombre? (obligatorio)");
            case 2:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("name", raw); activeFlow.nextStep();
                return ask("¿Descripción? (opcional, \"-\" para omitir)");
            case 3:
                d.put("description", isSkip(n) ? null : raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo catálogo", "Tipo", d.get("type"), "Código", d.get("code"),
                    "Nombre", d.get("name"), "Descripción", d.get("description")) + "\n\n¿Confirmar? (sí/no)");
            case 4:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_CATALOG", null, "~./zul/admin/catalogs.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateTenant(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("name", raw.toLowerCase().replaceAll("\\s+", "_")); activeFlow.nextStep();
                return ask("¿Nombre visible (display name)? (obligatorio)");
            case 1:
                if (raw.isBlank()) return ask("El nombre visible es obligatorio:");
                d.put("displayName", raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo tenant", "Identificador", d.get("name"), "Nombre visible", d.get("displayName"))
                    + "\n\n¿Confirmar? (sí/no)");
            case 2:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_TENANT", null, "~./zul/platform/tenants.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreateSchema(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El nombre del schema es obligatorio:");
                d.put("schemaName", raw.toLowerCase().replaceAll("\\s+", "_")); activeFlow.nextStep();
                return ask("¿Tipo de schema? (ej: core, hr, sales, documents)");
            case 1:
                if (raw.isBlank()) return ask("El tipo es obligatorio:");
                d.put("schemaType", raw.toLowerCase()); activeFlow.nextStep();
                return ask(buildSummary("Nuevo schema", "Nombre", d.get("schemaName"), "Tipo", d.get("schemaType"))
                    + "\n\n¿Confirmar? (sí/no)");
            case 2:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_SCHEMA", null, "~./zul/platform/schemas.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    private List<Response> processCreatePlatformUser(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        switch (activeFlow.getStep()) {
            case 0:
                if (raw.isBlank()) return ask("El username es obligatorio:");
                d.put("username", raw); activeFlow.nextStep();
                return ask("¿Contraseña? (obligatoria)");
            case 1:
                if (raw.isBlank()) return ask("La contraseña es obligatoria:");
                d.put("password", raw); activeFlow.nextStep();
                return ask("¿Email? (obligatorio)");
            case 2:
                if (raw.isBlank() || !raw.matches(".+@.+\\..+")) return ask("Email válido:");
                d.put("email", raw); activeFlow.nextStep();
                return ask("¿Nombre completo? (obligatorio)");
            case 3:
                if (raw.isBlank()) return ask("El nombre es obligatorio:");
                d.put("fullName", raw); activeFlow.nextStep();
                return ask(buildSummary("Nuevo admin plataforma", "Username", d.get("username"),
                    "Email", d.get("email"), "Nombre", d.get("fullName")) + "\n\n¿Confirmar? (sí/no)");
            case 4:
                if (isConfirm(n)) return List.of(new Response("EXEC_CREATE_PLATFORM_USER", null, "~./zul/platform/platform-users.zul"));
                activeFlow = null; return ask("Creación cancelada.");
            default: activeFlow = null; return ask("¿En qué más puedo ayudarte?");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Flujo genérico de EDICIÓN ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> processEditGeneric(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        String[][] fields = getFieldsForType();
        int step = activeFlow.getStep();

        if (step == 100) {
            // Guardar
            if (n.equals("guardar") || n.equals("g") || n.equals("save")) {
                return List.of(new Response(getExecCommand(), null, getNavPage()));
            }
            // Seleccionar campo
            try {
                int num = Integer.parseInt(n.trim());
                if (num >= 1 && num <= fields.length) {
                    String key = fields[num - 1][0];
                    if (key.equals("roles")) {
                        // Cargar roles disponibles
                        d.put("_editingField", String.valueOf(num - 1));
                        activeFlow.setStep(102);
                        return List.of(new Response("LOAD_ROLES"));
                    }
                    d.put("_editingField", String.valueOf(num - 1));
                    activeFlow.setStep(101);
                    String label = fields[num - 1][1];
                    String current = getCurrentFieldValue(key);
                    if (key.equals("enabled") || key.equals("active")) {
                        return ask("¿" + label + "? Actual: " + (current.equals("true") ? "sí" : "no") + ". Escribe \"sí\" o \"no\":");
                    }
                    return ask("Nuevo valor para " + label + " (actual: \"" + current + "\"):");
                }
            } catch (NumberFormatException ignored) {}
            return ask(buildEditMenu() + "\nCampo (número), \"guardar\" o \"cancelar\":");
        }

        if (step == 101) {
            int idx = Integer.parseInt(d.get("_editingField"));
            String key = fields[idx][0];
            if (key.equals("enabled") || key.equals("active")) {
                d.put("new_" + key, String.valueOf(isConfirm(n)));
            } else if (key.equals("password") && isSkip(n)) {
                // No cambiar contraseña
            } else {
                d.put("new_" + key, raw);
            }
            activeFlow.setStep(100);
            return ask(buildEditMenu() + "\nCampo (número), \"guardar\" o \"cancelar\":");
        }

        // Step 102: parsear selección de roles en modo edit
        if (step == 102) {
            List<Response> err = parseRoleSelection(raw);
            if (err != null) return err;
            // parseRoleSelection almacenó roleIds/roleNames y avanzó step
            d.put("new_roles", d.get("roleIds"));
            d.put("new_roles_display", d.get("roleNames"));
            activeFlow.setStep(100);
            return ask(buildEditMenu() + "\nCampo (número), \"guardar\" o \"cancelar\":");
        }

        activeFlow = null;
        return ask("Error interno.");
    }

    private String buildEditMenu() {
        String[][] fields = getFieldsForType();
        Map<String, String> sel = activeFlow.getSearchResults().get(activeFlow.getSelectedResultIndex());
        Map<String, String> d = activeFlow.getData();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String key = fields[i][0];
            String label = fields[i][1];
            if (key.equals("roles")) {
                String rolesDisplay = d.containsKey("new_roles_display")
                    ? d.get("new_roles_display") + " (modificado)"
                    : sel.getOrDefault("currentRoles", "(ninguno)");
                sb.append("  ").append(i + 1).append(". ").append(label).append(": ").append(rolesDisplay).append("\n");
                continue;
            }
            String newVal = d.get("new_" + key);
            String current = sel.getOrDefault(key, "");
            if (key.equals("password")) current = "****";
            if (newVal != null) {
                String display = (key.equals("enabled") || key.equals("active"))
                    ? (newVal.equals("true") ? "sí" : "no") : newVal;
                if (key.equals("password")) display = "****";
                sb.append("  ").append(i + 1).append(". ").append(label).append(": ").append(display).append(" (modificado)\n");
            } else {
                String display = (key.equals("enabled") || key.equals("active"))
                    ? (current.equals("true") ? "sí" : "no") : current;
                sb.append("  ").append(i + 1).append(". ").append(label).append(": ").append(display).append("\n");
            }
        }
        return sb.toString();
    }

    private String getCurrentFieldValue(String key) {
        Map<String, String> sel = activeFlow.getSearchResults().get(activeFlow.getSelectedResultIndex());
        String newVal = activeFlow.getData().get("new_" + key);
        return newVal != null ? newVal : sel.getOrDefault(key, "");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Flujo genérico de ELIMINACIÓN ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> processDeleteGeneric(String n) {
        if (activeFlow.getStep() == 100) {
            if (isConfirm(n)) {
                return List.of(new Response(getExecCommand(), null, getNavPage()));
            }
            activeFlow = null;
            return ask("Eliminación cancelada.");
        }
        activeFlow = null;
        return ask("Error interno.");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Flujo ASIGNAR PERMISOS ──
    // ══════════════════════════════════════════════════════════════

    private List<Response> processAssignPermissions(String n, String raw) {
        Map<String, String> d = activeFlow.getData();
        int step = activeFlow.getStep();

        if (step == 100) {
            // Usuario ingresa schema types
            String available = d.getOrDefault("availableSchemas", "");
            Set<String> availableSet = new LinkedHashSet<>();
            for (String s : available.split(",")) { s = s.trim(); if (!s.isEmpty()) availableSet.add(s); }

            String[] input = raw.split("[,;\\s]+");
            List<String> valid = new ArrayList<>();
            for (String s : input) {
                s = s.trim().toLowerCase();
                if (!s.isEmpty() && availableSet.contains(s)) valid.add(s);
            }
            if (valid.isEmpty()) {
                return ask("No ingresaste esquemas válidos. Disponibles: " + available + "\nIntenta de nuevo:");
            }
            d.put("newSchemas", String.join(",", valid));
            activeFlow.setStep(101);
            Map<String, String> sel = activeFlow.getSearchResults().get(activeFlow.getSelectedResultIndex());
            return ask("¿Asignar permisos [" + String.join(", ", valid) + "] al rol \"" + sel.get("name") + "\"? (sí/no)");
        }

        if (step == 101) {
            if (isConfirm(n)) {
                return List.of(new Response("EXEC_ASSIGN_PERMISSIONS", null, "~./zul/admin/role-schemas.zul"));
            }
            activeFlow = null;
            return ask("Asignación cancelada.");
        }

        activeFlow = null;
        return ask("Error interno.");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Mensajes de ayuda ──
    // ══════════════════════════════════════════════════════════════

    private String buildGreeting(Set<String> schemas, boolean isAdmin, boolean isPlatformAdmin) {
        StringBuilder sb = new StringBuilder("¡Hola! Soy tu asistente. Puedo navegar, crear, editar y eliminar registros.\n\n");
        sb.append("Ejemplos rápidos:\n");
        sb.append("  \"ir a inicio\"\n");
        if (schemas.contains("sales")) sb.append("  \"crear cliente\", \"ir a tareas\"\n");
        if (schemas.contains("hr")) sb.append("  \"crear empleado\", \"editar empleado\", \"eliminar empleado\", \"ir a empleados\"\n");
        if (isAdmin || isPlatformAdmin) {
            sb.append("  \"crear rol\", \"editar usuario admin\", \"eliminar catálogo\"\n");
            sb.append("  \"asignar permisos\"\n");
        }
        if (isPlatformAdmin) sb.append("  \"crear tenant\", \"editar tenant acme\"\n");
        sb.append("\nEscribe \"ayuda\" para la lista completa.");
        return sb.toString();
    }

    private String buildHelp(Set<String> schemas, boolean isAdmin, boolean isPlatformAdmin) {
        StringBuilder sb = new StringBuilder();
        sb.append("Usa frases como \"ir a ...\", \"crear ...\", \"editar ... [nombre]\", \"eliminar ... [nombre]\".\n\n");

        sb.append("--- Navegación ---\n");
        sb.append("  ir a inicio / dashboard\n");
        if (schemas.contains("sales")) { sb.append("  ir a clientes\n  ir a tareas / procesos\n"); }
        if (schemas.contains("hr")) sb.append("  ir a empleados\n");
        if (isAdmin || isPlatformAdmin) sb.append("  ir a roles / usuarios / catálogos / permisos\n");
        if (isPlatformAdmin) sb.append("  ir a tenants / schemas / admins plataforma\n");

        if (schemas.contains("sales") || schemas.contains("hr")) {
            sb.append("\n--- Módulos de negocio ---\n");
            if (schemas.contains("sales")) {
                sb.append("  crear cliente → Nombre, email, teléfono\n");
                sb.append("  iniciar proceso / nueva venta → Pedido, monto\n");
            }
            if (schemas.contains("hr")) {
                sb.append("  crear empleado → Nombre, email, cargo, contraseña\n");
                sb.append("  editar empleado [nombre] / eliminar empleado [nombre]\n");
            }
        }

        if (isAdmin || isPlatformAdmin) {
            sb.append("\n--- Roles (admin) ---\n");
            sb.append("  crear rol / editar rol [nombre] / eliminar rol [nombre]\n");
            sb.append("\n--- Usuarios (admin) ---\n");
            sb.append("  crear usuario / editar usuario [nombre] / eliminar usuario [nombre]\n");
            sb.append("\n--- Catálogos (admin) ---\n");
            sb.append("  crear catálogo / editar catálogo [nombre] / eliminar catálogo [nombre]\n");
            sb.append("\n--- Permisos (admin) ---\n");
            sb.append("  asignar permisos → Selecciona rol y schemas\n");
        }

        if (isPlatformAdmin) {
            sb.append("\n--- Tenants (platform) ---\n");
            sb.append("  crear tenant / editar tenant [nombre]\n");
            sb.append("\n--- Schemas (platform) ---\n");
            sb.append("  crear schema\n");
            sb.append("\n--- Admins plataforma ---\n");
            sb.append("  crear admin plataforma / editar admin plataforma [nombre]\n");
            sb.append("  eliminar admin plataforma [nombre]\n");
        }

        sb.append("\n--- General ---\n");
        sb.append("  hola → Saludo\n  ayuda → Este mensaje\n  cancelar → Cancelar operación en curso\n");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Utilidades ──
    // ══════════════════════════════════════════════════════════════

    private String normalize(String input) {
        return input.toLowerCase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n");
    }

    private List<Response> ask(String text) { return List.of(new Response(text)); }

    private boolean isSkip(String n) {
        return n.equals("-") || n.equals("omitir") || n.equals("saltar") || n.equals("ninguno") || n.equals("na") || n.equals("n/a");
    }

    private boolean isConfirm(String n) {
        return n.equals("si") || n.equals("s") || n.equals("yes") || n.equals("y") || n.equals("confirmar") || n.equals("ok");
    }

    private boolean hasAccess(String access, Set<String> schemas, boolean isAdmin, boolean isPlatformAdmin) {
        switch (access) {
            case "all": return true;
            case "platform_admin": return isPlatformAdmin;
            case "admin": return isAdmin || isPlatformAdmin;
            default: return schemas.contains(access);
        }
    }

    private boolean isEditFlow() {
        if (activeFlow == null) return false;
        switch (activeFlow.getType()) {
            case EDIT_EMPLOYEE:
            case EDIT_ROLE: case EDIT_USER: case EDIT_CATALOG:
            case EDIT_TENANT: case EDIT_PLATFORM_USER: return true;
            default: return false;
        }
    }

    private boolean isDeleteFlow() {
        if (activeFlow == null) return false;
        switch (activeFlow.getType()) {
            case DELETE_EMPLOYEE:
            case DELETE_ROLE: case DELETE_USER: case DELETE_CATALOG:
            case DELETE_PLATFORM_USER: return true;
            default: return false;
        }
    }

    private boolean isSearchableFlow() {
        if (activeFlow == null) return false;
        return isEditFlow() || isDeleteFlow() || activeFlow.getType() == FlowType.ASSIGN_PERMISSIONS;
    }

    private String getEntityCode() {
        if (activeFlow == null) return "";
        switch (activeFlow.getType()) {
            case EDIT_EMPLOYEE: case DELETE_EMPLOYEE: return "EMPLOYEE";
            case EDIT_ROLE: case DELETE_ROLE: return "ROLE";
            case EDIT_USER: case DELETE_USER: return "USER";
            case EDIT_CATALOG: case DELETE_CATALOG: return "CATALOG";
            case EDIT_TENANT: return "TENANT";
            case EDIT_PLATFORM_USER: case DELETE_PLATFORM_USER: return "PLATFORM_USER";
            case ASSIGN_PERMISSIONS: return "ROLE_PERMS";
            default: return "";
        }
    }

    private String[][] getFieldsForType() {
        if (activeFlow == null) return new String[0][];
        switch (activeFlow.getType()) {
            case EDIT_EMPLOYEE: return EMPLOYEE_FIELDS;
            case EDIT_ROLE: return ROLE_FIELDS;
            case EDIT_USER: return USER_FIELDS;
            case EDIT_CATALOG: return CATALOG_FIELDS;
            case EDIT_TENANT: return TENANT_FIELDS;
            case EDIT_PLATFORM_USER: return PLATFORM_USER_FIELDS;
            default: return new String[0][];
        }
    }

    private String getExecCommand() {
        if (activeFlow == null) return "";
        switch (activeFlow.getType()) {
            case EDIT_EMPLOYEE: return "EXEC_UPDATE_EMPLOYEE";
            case DELETE_EMPLOYEE: return "EXEC_DELETE_EMPLOYEE";
            case EDIT_ROLE: return "EXEC_UPDATE_ROLE";
            case DELETE_ROLE: return "EXEC_DELETE_ROLE";
            case EDIT_USER: return "EXEC_UPDATE_USER";
            case DELETE_USER: return "EXEC_DELETE_USER";
            case EDIT_CATALOG: return "EXEC_UPDATE_CATALOG";
            case DELETE_CATALOG: return "EXEC_DELETE_CATALOG";
            case EDIT_TENANT: return "EXEC_UPDATE_TENANT";
            case EDIT_PLATFORM_USER: return "EXEC_UPDATE_PLATFORM_USER";
            case DELETE_PLATFORM_USER: return "EXEC_DELETE_PLATFORM_USER";
            case ASSIGN_PERMISSIONS: return "EXEC_ASSIGN_PERMISSIONS";
            default: return "";
        }
    }

    private String getNavPage() {
        if (activeFlow == null) return null;
        switch (activeFlow.getType()) {
            case EDIT_EMPLOYEE: case DELETE_EMPLOYEE: return "~./zul/employees/list.zul";
            case EDIT_ROLE: case DELETE_ROLE: case CREATE_ROLE: return "~./zul/admin/roles.zul";
            case EDIT_USER: case DELETE_USER: case CREATE_USER: return "~./zul/admin/users.zul";
            case EDIT_CATALOG: case DELETE_CATALOG: case CREATE_CATALOG: return "~./zul/admin/catalogs.zul";
            case EDIT_TENANT: case CREATE_TENANT: return "~./zul/platform/tenants.zul";
            case EDIT_PLATFORM_USER: case DELETE_PLATFORM_USER: case CREATE_PLATFORM_USER: return "~./zul/platform/platform-users.zul";
            case ASSIGN_PERMISSIONS: return "~./zul/admin/role-schemas.zul";
            case CREATE_SCHEMA: return "~./zul/platform/schemas.zul";
            default: return null;
        }
    }

    private String buildSummary(String title, String... pairs) {
        StringBuilder sb = new StringBuilder("Resumen — " + title + ":\n");
        for (int i = 0; i < pairs.length; i += 2) {
            String val = pairs[i + 1];
            sb.append("  - ").append(pairs[i]).append(": ").append(val != null ? val : "(vacío)").append("\n");
        }
        return sb.toString();
    }
}
