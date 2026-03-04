package com.lreyes.platform.modules.employees.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String position,
        String department,
        LocalDate hireDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
