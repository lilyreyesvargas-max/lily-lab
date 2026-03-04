package com.lreyes.platform.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String orderNumber,
        @NotBlank String customerName,
        String seller,
        String description,
        @NotEmpty @Valid List<OrderLineRequest> lines
) {}
