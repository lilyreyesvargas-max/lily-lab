package com.lreyes.platform.modules.customers;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.modules.customers.event.CustomerCreatedEvent;
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
public class CustomerService implements CustomerServicePort {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final DomainEventPublisher eventPublisher;

    public long count() {
        return customerRepository.count();
    }

    public PageResponse<CustomerResponse> findAll(String search, Pageable pageable) {
        Page<Customer> page;
        if (search != null && !search.isBlank()) {
            page = customerRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        } else {
            page = customerRepository.findAll(pageable);
        }
        return PageResponse.from(page, customerMapper::toResponse);
    }

    public CustomerResponse findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer", id));
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        Customer customer = customerMapper.toEntity(request);
        customer.setActive(true);
        Customer saved = customerRepository.save(customer);

        eventPublisher.publish(
                new CustomerCreatedEvent(
                        TenantContext.getCurrentTenant(),
                        saved.getId(),
                        saved.getName()),
                "customer",
                saved.getId());

        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer", id));
        customerMapper.updateEntity(request, customer);
        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw new EntityNotFoundException("Customer", id);
        }
        customerRepository.deleteById(id);
    }
}
