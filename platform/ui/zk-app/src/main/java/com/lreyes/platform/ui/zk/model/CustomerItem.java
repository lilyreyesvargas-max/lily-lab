package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;

/**
 * Modelo UI para cliente (mock, Step 9 lo reemplazará con DTO real).
 */
public class CustomerItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String email;
    private String phone;

    public CustomerItem() {}

    public CustomerItem(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
