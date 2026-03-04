package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class UserItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private String email;
    private String fullName;
    private String password;
    private boolean enabled = true;
    private Set<String> roleIds = new HashSet<>();
    private String rolesDisplay;

    public UserItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getRoleIds() { return roleIds; }
    public void setRoleIds(Set<String> roleIds) { this.roleIds = roleIds; }
    public String getRolesDisplay() { return rolesDisplay; }
    public void setRolesDisplay(String rolesDisplay) { this.rolesDisplay = rolesDisplay; }
}
