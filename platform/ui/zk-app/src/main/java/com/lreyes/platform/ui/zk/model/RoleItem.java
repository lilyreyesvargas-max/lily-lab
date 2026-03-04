package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;

public class RoleItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;

    public RoleItem() {}

    public RoleItem(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
