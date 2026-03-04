package com.lreyes.platform.modules.customers.dto;

import jakarta.validation.constraints.Email;

public record UpdateCustomerRequest(
        String name,
        @Email String email,
        String phone,
        String address,
        Boolean active
) {}
