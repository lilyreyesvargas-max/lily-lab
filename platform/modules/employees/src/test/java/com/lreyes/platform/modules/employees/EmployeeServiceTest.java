package com.lreyes.platform.modules.employees;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.modules.employees.dto.EmployeeResponse;
import com.lreyes.platform.modules.employees.dto.UpdateEmployeeRequest;
import com.lreyes.platform.modules.employees.event.EmployeeCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private EmployeeResponse employeeResponse;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();
        employee = new Employee();
        employee.setId(employeeId);
        employee.setFirstName("Juan");
        employee.setLastName("Perez");
        employee.setEmail("juan@example.com");
        employee.setPosition("Developer");
        employee.setDepartment("IT");
        employee.setHireDate(LocalDate.of(2024, 1, 15));
        employee.setActive(true);
        employee.setCreatedAt(Instant.now());

        employeeResponse = new EmployeeResponse(
                employeeId, "Juan", "Perez", "juan@example.com",
                "Developer", "IT", LocalDate.of(2024, 1, 15),
                true, employee.getCreatedAt(), null);
    }

    @Test
    @DisplayName("create - guarda empleado y publica evento")
    void create_savesAndPublishesEvent() {
        var request = new CreateEmployeeRequest(
                "Juan", "Perez", "juan@example.com", "Developer", "IT", LocalDate.of(2024, 1, 15));
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(employeeMapper.toResponse(employee)).thenReturn(employeeResponse);

        EmployeeResponse result = employeeService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.firstName()).isEqualTo("Juan");
        verify(employeeRepository).save(any(Employee.class));

        ArgumentCaptor<EmployeeCreatedEvent> captor = ArgumentCaptor.forClass(EmployeeCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture(), eq("employee"), eq(employeeId));
        assertThat(captor.getValue().getEmployeeId()).isEqualTo(employeeId);
        assertThat(captor.getValue().getFullName()).isEqualTo("Juan Perez");
        assertThat(captor.getValue().eventType()).isEqualTo("employee.created");
    }

    @Test
    @DisplayName("findById - retorna empleado encontrado")
    void findById_found() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeMapper.toResponse(employee)).thenReturn(employeeResponse);

        EmployeeResponse result = employeeService.findById(employeeId);

        assertThat(result.id()).isEqualTo(employeeId);
        verify(employeeRepository).findById(employeeId);
    }

    @Test
    @DisplayName("findById - lanza excepción si no existe")
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("update - actualiza campos del empleado")
    void update_modifiesEntity() {
        var request = new UpdateEmployeeRequest("Juan", "Garcia", null, null, null, null, null);
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toResponse(employee)).thenReturn(employeeResponse);

        EmployeeResponse result = employeeService.update(employeeId, request);

        assertThat(result).isNotNull();
        verify(employeeMapper).updateEntity(request, employee);
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("delete - elimina empleado existente")
    void delete_existing() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        employeeService.delete(employeeId);

        verify(employeeRepository).delete(employee);
    }

    @Test
    @DisplayName("delete - lanza excepción si no existe")
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
        verify(employeeRepository, never()).delete(any());
    }
}
