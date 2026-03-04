package com.lreyes.platform.modules.sales;

import com.lreyes.platform.modules.sales.dto.CreateOrderRequest;
import com.lreyes.platform.modules.sales.dto.OrderLineRequest;
import com.lreyes.platform.modules.sales.dto.OrderLineResponse;
import com.lreyes.platform.modules.sales.dto.OrderResponse;
import com.lreyes.platform.shared.mapping.DefaultMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = DefaultMapStructConfig.class)
public interface SalesOrderMapper {

    @Mapping(target = "lines", source = "lines")
    OrderResponse toResponse(SalesOrder entity);

    OrderLineResponse toLineResponse(OrderLine line);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "processInstanceId", ignore = true)
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    SalesOrder toEntity(CreateOrderRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "salesOrder", ignore = true)
    @Mapping(target = "lineTotal", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    OrderLine toLineEntity(OrderLineRequest dto);

    List<OrderResponse> toResponseList(List<SalesOrder> entities);
}
