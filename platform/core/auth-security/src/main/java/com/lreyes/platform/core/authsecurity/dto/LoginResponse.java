package com.lreyes.platform.core.authsecurity.dto;

public record LoginResponse(
        String token,
        String username,
        String tenantId,
        java.util.List<String> roles,
        long expiresInSeconds
) {}
