package com.lreyes.platform.modules.employees;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.modules.employees.dto.EmployeeResponse;
import com.lreyes.platform.modules.employees.dto.UpdateEmployeeRequest;
import com.lreyes.platform.modules.employees.event.EmployeeCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;
    private final DomainEventPublisher eventPublisher;

    public long count() {
        return employeeRepository.count();
    }

    public PageResponse<EmployeeResponse> findAll(String search, Pageable pageable) {
        Page<Employee> page;
        if (search != null && !search.isBlank()) {
            page = employeeRepository
                    .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                            search, search, pageable);
        } else {
            page = employeeRepository.findAll(pageable);
        }
        return PageResponse.from(page, employeeMapper::toResponse);
    }

    public EmployeeResponse findById(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee", id));
        return employeeMapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        Employee employee = employeeMapper.toEntity(request);
        employee.setActive(true);
        Employee saved = employeeRepository.save(employee);

        eventPublisher.publish(
                new EmployeeCreatedEvent(
                        TenantContext.getCurrentTenant(),
                        saved.getId(),
                        saved.getFirstName() + " " + saved.getLastName()),
                "employee",
                saved.getId());

        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee", id));
        employeeMapper.updateEntity(request, employee);
        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee", id));
        employeeRepository.delete(employee);
    }
}
