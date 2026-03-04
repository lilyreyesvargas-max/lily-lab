package com.lreyes.platform.modules.employees.event;

import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class EmployeeCreatedEvent extends DomainEvent {

    private final UUID employeeId;
    private final String fullName;

    public EmployeeCreatedEvent(String tenantId, UUID employeeId, String fullName) {
        super(tenantId);
        this.employeeId = employeeId;
        this.fullName = fullName;
    }

    @Override
    public String eventType() {
        return "employee.created";
    }
}
