package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;

public class RoleCheckItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private boolean checked;

    public RoleCheckItem() {}

    public RoleCheckItem(String id, String name, boolean checked) {
        this.id = id;
        this.name = name;
        this.checked = checked;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
