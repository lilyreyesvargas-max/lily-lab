package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.Role;
import com.lreyes.platform.core.tenancy.RoleService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.User;
import com.lreyes.platform.core.tenancy.UserService;
import com.lreyes.platform.modules.employees.EmployeeRepository;
import com.lreyes.platform.modules.employees.EmployeeService;
import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.ui.zk.model.RoleCheckItem;
import com.lreyes.platform.ui.zk.model.UiUser;
import com.lreyes.platform.ui.zk.model.UserItem;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class UserListVM {

    private UserService userService;
    private RoleService roleService;
    private UiUser uiUser;

    private List<UserItem> users = new ArrayList<>();
    private List<UserItem> allUsers = new ArrayList<>();
    private UserItem selectedUser;
    private UserItem editingUser = new UserItem();
    private List<RoleCheckItem> roleChecks = new ArrayList<>();
    private String searchTerm;
    private boolean editing;
    private boolean newRecord;

    @Init
    public void init() {
        uiUser = (UiUser) Sessions.getCurrent().getAttribute("user");
        userService = SpringUtil.getApplicationContext().getBean(UserService.class);
        roleService = SpringUtil.getApplicationContext().getBean(RoleService.class);
        loadData();
    }

    private void loadData() {
        TenantContext.setCurrentTenant(uiUser.getTenantId());
        List<User> entities = userService.findAll();
        allUsers = new ArrayList<>();
        for (User u : entities) {
            UserItem item = new UserItem();
            item.setId(u.getId().toString());
            item.setUsername(u.getUsername());
            item.setEmail(u.getEmail());
            item.setFullName(u.getFullName());
            item.setEnabled(u.isEnabled());
            Set<String> rIds = new HashSet<>();
            StringBuilder display = new StringBuilder();
            for (Role r : u.getRoles()) {
                rIds.add(r.getId().toString());
                if (display.length() > 0) display.append(", ");
                display.append(r.getName());
            }
            item.setRoleIds(rIds);
            item.setRolesDisplay(display.toString());
            allUsers.add(item);
        }
        users = new ArrayList<>(allUsers);
    }

    private void loadRoleChecks(Set<String> selectedRoleIds) {
        TenantContext.setCurrentTenant(uiUser.getTenantId());
        List<Role> allRoles = roleService.findAll();
        roleChecks = new ArrayList<>();
        for (Role r : allRoles) {
            boolean checked = selectedRoleIds != null && selectedRoleIds.contains(r.getId().toString());
            roleChecks.add(new RoleCheckItem(r.getId().toString(), r.getName(), checked));
        }
    }

    @Command
    @NotifyChange("users")
    public void search() {
        if (searchTerm == null || searchTerm.isBlank()) {
            users = new ArrayList<>(allUsers);
        } else {
            String term = searchTerm.toLowerCase();
            users = allUsers.stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(term)
                            || u.getEmail().toLowerCase().contains(term)
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(term)))
                    .toList();
        }
    }

    @Command
    @NotifyChange({"editing", "editingUser", "newRecord", "roleChecks"})
    public void openNew() {
        editingUser = new UserItem();
        loadRoleChecks(null);
        editing = true;
        newRecord = true;
    }

    @Command
    @NotifyChange({"editing", "editingUser", "newRecord", "roleChecks"})
    public void edit(@BindingParam("user") UserItem u) {
        editingUser = new UserItem();
        editingUser.setId(u.getId());
        editingUser.setUsername(u.getUsername());
        editingUser.setEmail(u.getEmail());
        editingUser.setFullName(u.getFullName());
        editingUser.setEnabled(u.isEnabled());
        editingUser.setRoleIds(new HashSet<>(u.getRoleIds()));
        loadRoleChecks(u.getRoleIds());
        editing = true;
        newRecord = false;
    }

    @Command
    @NotifyChange({"users", "editing", "editingUser"})
    public void save() {
        if (editingUser.getUsername() == null || editingUser.getUsername().isBlank()) {
            Clients.showNotification("El username es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }
        if (editingUser.getEmail() == null || editingUser.getEmail().isBlank()) {
            Clients.showNotification("El email es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }
        if (editingUser.getFullName() == null || editingUser.getFullName().isBlank()) {
            Clients.showNotification("El nombre completo es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }

        Set<UUID> roleIds = roleChecks.stream()
                .filter(RoleCheckItem::isChecked)
                .map(rc -> UUID.fromString(rc.getId()))
                .collect(Collectors.toSet());

        TenantContext.setCurrentTenant(uiUser.getTenantId());
        if (newRecord) {
            User user = new User();
            user.setUsername(editingUser.getUsername());
            user.setEmail(editingUser.getEmail());
            user.setFullName(editingUser.getFullName());
            user.setEnabled(editingUser.isEnabled());
            userService.create(user, editingUser.getPassword(), roleIds);

            String email = editingUser.getEmail();
            if (email != null && !email.isBlank()) {
                EmployeeRepository empRepo = SpringUtil.getApplicationContext().getBean(EmployeeRepository.class);
                if (empRepo.findByEmail(email).isEmpty()) {
                    EmployeeService empService = SpringUtil.getApplicationContext().getBean(EmployeeService.class);
                    String fullName = editingUser.getFullName().trim();
                    int spaceIdx = fullName.indexOf(' ');
                    String firstName;
                    String lastName;
                    if (spaceIdx > 0) {
                        firstName = fullName.substring(0, spaceIdx);
                        lastName = fullName.substring(spaceIdx + 1);
                    } else {
                        firstName = fullName;
                        lastName = fullName;
                    }
                    empService.create(new CreateEmployeeRequest(firstName, lastName, email, null, null, null));
                    Clients.showNotification("Usuario y empleado creados", "info", null, "end_center", 1500);
                } else {
                    Clients.showNotification("Usuario creado", "info", null, "end_center", 1500);
                }
            } else {
                Clients.showNotification("Usuario creado", "info", null, "end_center", 1500);
            }
        } else {
            userService.update(
                    UUID.fromString(editingUser.getId()),
                    editingUser.getUsername(),
                    editingUser.getEmail(),
                    editingUser.getFullName(),
                    editingUser.isEnabled(),
                    editingUser.getPassword(),
                    roleIds);
            Clients.showNotification("Usuario actualizado", "info", null, "end_center", 1500);
        }

        loadData();
        editing = false;
        editingUser = new UserItem();
    }

    @Command
    @NotifyChange({"editing", "editingUser"})
    public void cancelEdit() {
        editing = false;
        editingUser = new UserItem();
    }

    @Command
    @NotifyChange("users")
    public void delete(@BindingParam("user") UserItem u) {
        TenantContext.setCurrentTenant(uiUser.getTenantId());
        userService.delete(UUID.fromString(u.getId()));
        loadData();
        Clients.showNotification("Usuario eliminado", "info", null, "end_center", 1500);
    }

    // ── Getters / Setters ──

    public String getFormTitle() {
        return newRecord ? "Nuevo Usuario" : "Editar Usuario";
    }

    public List<UserItem> getUsers() { return users; }
    public UserItem getSelectedUser() { return selectedUser; }
    public void setSelectedUser(UserItem selectedUser) { this.selectedUser = selectedUser; }
    public UserItem getEditingUser() { return editingUser; }
    public void setEditingUser(UserItem editingUser) { this.editingUser = editingUser; }
    public List<RoleCheckItem> getRoleChecks() { return roleChecks; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public boolean isEditing() { return editing; }
    public boolean isNewRecord() { return newRecord; }
}
