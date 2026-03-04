package com.lreyes.platform.modules.customers.event;

import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CustomerCreatedEvent extends DomainEvent {

    private final UUID customerId;
    private final String name;

    public CustomerCreatedEvent(String tenantId, UUID customerId, String name) {
        super(tenantId);
        this.customerId = customerId;
        this.name = name;
    }

    @Override
    public String eventType() {
        return "customer.created";
    }
}
