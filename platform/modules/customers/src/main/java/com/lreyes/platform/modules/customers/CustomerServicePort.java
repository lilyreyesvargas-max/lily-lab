package com.lreyes.platform.modules.customers;

import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerServicePort {
    long count();
    PageResponse<CustomerResponse> findAll(String search, Pageable pageable);
    CustomerResponse findById(UUID id);
    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse update(UUID id, UpdateCustomerRequest request);
    void delete(UUID id);
}
