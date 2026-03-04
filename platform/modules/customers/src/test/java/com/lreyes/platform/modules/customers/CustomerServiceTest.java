package com.lreyes.platform.modules.customers;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.modules.customers.event.CustomerCreatedEvent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private CustomerService customerService;

    private Customer customer;
    private CustomerResponse customerResponse;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = new Customer();
        customer.setId(customerId);
        customer.setName("Acme Corp");
        customer.setEmail("contact@acme.com");
        customer.setPhone("+1234567890");
        customer.setAddress("123 Main St");
        customer.setActive(true);
        customer.setCreatedAt(Instant.now());

        customerResponse = new CustomerResponse(
                customerId, "Acme Corp", "contact@acme.com",
                "+1234567890", "123 Main St", true,
                customer.getCreatedAt(), null);
    }

    @Test
    @DisplayName("create - guarda cliente y publica evento")
    void create_savesAndPublishesEvent() {
        var request = new CreateCustomerRequest("Acme Corp", "contact@acme.com", "+1234567890", "123 Main St");
        when(customerMapper.toEntity(request)).thenReturn(customer);
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

        CustomerResponse result = customerService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Acme Corp");
        verify(customerRepository).save(any(Customer.class));

        ArgumentCaptor<CustomerCreatedEvent> captor = ArgumentCaptor.forClass(CustomerCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture(), eq("customer"), eq(customerId));
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(captor.getValue().eventType()).isEqualTo("customer.created");
    }

    @Test
    @DisplayName("findById - retorna cliente encontrado")
    void findById_found() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

        CustomerResponse result = customerService.findById(customerId);

        assertThat(result.id()).isEqualTo(customerId);
        verify(customerRepository).findById(customerId);
    }

    @Test
    @DisplayName("findById - lanza excepción si no existe")
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("update - actualiza campos del cliente")
    void update_modifiesEntity() {
        var request = new UpdateCustomerRequest("Acme Updated", null, null, null, null);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

        CustomerResponse result = customerService.update(customerId, request);

        assertThat(result).isNotNull();
        verify(customerMapper).updateEntity(request, customer);
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("delete - elimina cliente existente")
    void delete_existing() {
        when(customerRepository.existsById(customerId)).thenReturn(true);

        customerService.delete(customerId);

        verify(customerRepository).deleteById(customerId);
    }

    @Test
    @DisplayName("delete - lanza excepción si no existe")
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> customerService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
        verify(customerRepository, never()).deleteById(any());
    }
}
