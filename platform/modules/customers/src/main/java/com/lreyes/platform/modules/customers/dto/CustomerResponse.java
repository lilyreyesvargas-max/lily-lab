package com.lreyes.platform.modules.customers.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
