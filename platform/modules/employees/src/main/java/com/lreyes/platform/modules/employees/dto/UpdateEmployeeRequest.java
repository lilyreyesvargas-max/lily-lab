package com.lreyes.platform.modules.employees.dto;

import java.time.LocalDate;

public record UpdateEmployeeRequest(
        String firstName,
        String lastName,
        String email,
        String position,
        String department,
        LocalDate hireDate,
        Boolean active
) {}
