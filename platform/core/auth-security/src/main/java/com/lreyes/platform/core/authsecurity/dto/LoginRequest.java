package com.lreyes.platform.core.authsecurity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String tenantId,
        java.util.List<String> roles
) {}
