package com.lreyes.platform.modules.sales.event;

import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class OrderCreatedEvent extends DomainEvent {

    private final UUID orderId;
    private final String orderNumber;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(String tenantId, UUID orderId, String orderNumber, BigDecimal totalAmount) {
        super(tenantId);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
    }

    @Override
    public String eventType() {
        return "order.created";
    }
}
