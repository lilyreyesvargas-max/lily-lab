package com.lreyes.platform.modules.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderLineRequest(
        @NotBlank String productName,
        @Min(1) int quantity,
        @NotNull BigDecimal unitPrice
) {}
