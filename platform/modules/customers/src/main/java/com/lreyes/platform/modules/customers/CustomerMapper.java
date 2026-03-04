package com.lreyes.platform.modules.customers;

import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.shared.mapping.DefaultMapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(config = DefaultMapStructConfig.class)
public interface CustomerMapper {

    CustomerResponse toResponse(Customer entity);

    Customer toEntity(CreateCustomerRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateCustomerRequest dto, @MappingTarget Customer entity);

    List<CustomerResponse> toResponseList(List<Customer> entities);
}
