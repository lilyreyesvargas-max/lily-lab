package com.lreyes.platform.modules.customers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<Customer> findByEmail(String email);

    Page<Customer> findByActiveTrue(Pageable pageable);
}
