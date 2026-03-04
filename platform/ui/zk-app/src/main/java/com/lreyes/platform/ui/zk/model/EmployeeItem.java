package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;

/**
 * Modelo UI para empleado (mock, Step 9 lo reemplazará con DTO real).
 */
public class EmployeeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String position;

    public EmployeeItem() {}

    public EmployeeItem(String id, String firstName, String lastName,
                        String email, String position) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.position = position;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getFullName() { return firstName + " " + lastName; }
}
