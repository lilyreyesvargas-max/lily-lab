package com.lreyes.platform.modules.employees;

import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.modules.employees.dto.EmployeeResponse;
import com.lreyes.platform.modules.employees.dto.UpdateEmployeeRequest;
import com.lreyes.platform.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeServicePort {
    long count();
    PageResponse<EmployeeResponse> findAll(String search, Pageable pageable);
    EmployeeResponse findById(UUID id);
    Optional<EmployeeResponse> findByEmail(String email);
    EmployeeResponse create(CreateEmployeeRequest request);
    EmployeeResponse update(UUID id, UpdateEmployeeRequest request);
    void delete(UUID id);
}
