package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;

/**
 * Ítem del menú de navegación lateral.
 */
public class MenuItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String label;
    private final String page;
    private final String requiredRole;

    public MenuItem(String id, String label, String page, String requiredRole) {
        this.id = id;
        this.label = label;
        this.page = page;
        this.requiredRole = requiredRole;
    }

    public MenuItem(String id, String label, String page) {
        this(id, label, page, null);
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getPage() { return page; }
    public String getRequiredRole() { return requiredRole; }
}
