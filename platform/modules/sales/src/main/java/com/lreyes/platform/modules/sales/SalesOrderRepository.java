package com.lreyes.platform.modules.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    Optional<SalesOrder> findByOrderNumber(String orderNumber);

    Page<SalesOrder> findByStatus(OrderStatus status, Pageable pageable);

    Page<SalesOrder> findByCustomerNameContainingIgnoreCase(String customerName, Pageable pageable);
}
