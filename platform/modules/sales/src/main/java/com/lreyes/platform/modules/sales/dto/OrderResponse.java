package com.lreyes.platform.modules.sales.dto;

import com.lreyes.platform.modules.sales.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String customerName,
        String seller,
        String description,
        BigDecimal totalAmount,
        OrderStatus status,
        String processInstanceId,
        List<OrderLineResponse> lines,
        Instant createdAt,
        Instant updatedAt
) {}
