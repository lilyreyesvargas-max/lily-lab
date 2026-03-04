package com.lreyes.platform.modules.employees;

import com.lreyes.platform.modules.employees.dto.CreateEmployeeRequest;
import com.lreyes.platform.modules.employees.dto.EmployeeResponse;
import com.lreyes.platform.modules.employees.dto.UpdateEmployeeRequest;
import com.lreyes.platform.shared.mapping.DefaultMapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(config = DefaultMapStructConfig.class)
public interface EmployeeMapper {

    EmployeeResponse toResponse(Employee entity);

    Employee toEntity(CreateEmployeeRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateEmployeeRequest dto, @MappingTarget Employee entity);

    List<EmployeeResponse> toResponseList(List<Employee> entities);
}
