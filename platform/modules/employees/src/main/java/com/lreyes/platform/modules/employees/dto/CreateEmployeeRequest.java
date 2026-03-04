package com.lreyes.platform.modules.employees.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email String email,
        String position,
        String department,
        LocalDate hireDate
) {}
