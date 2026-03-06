package com.lreyes.platform.modules.customers;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración de CustomerService con H2 en memoria.
 * Usa @DataJpaTest para un contexto JPA ligero, sin necesidad de
 * la aplicación Spring Boot completa ni Docker.
 */
@DataJpaTest(excludeAutoConfiguration = FlywayAutoConfiguration.class)
@Import({CustomerService.class, CustomerMapperImpl.class, TestJpaAuditingConfig.class})
class CustomerServiceIT {

    @Autowired
    private CustomerService customerService;

    @MockBean
    private DomainEventPublisher eventPublisher;

    @BeforeEach
    void setup() {
        TenantContext.setCurrentTenant("test");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createAndFind() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("Acme Corp", "acme@example.com", null, null));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Acme Corp");

        CustomerResponse found = customerService.findById(created.id());
        assertThat(found.name()).isEqualTo("Acme Corp");
        assertThat(found.email()).isEqualTo("acme@example.com");
    }

    @Test
    void updateCustomer() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("Before", "before@example.com", null, null));

        customerService.update(created.id(),
                new UpdateCustomerRequest("After", null, null, null, null));

        CustomerResponse updated = customerService.findById(created.id());
        assertThat(updated.name()).isEqualTo("After");
    }

    @Test
    void deleteCustomer() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("To Delete", "del@example.com", null, null));

        customerService.delete(created.id());

        assertThatThrownBy(() -> customerService.findById(created.id()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findAll_search_filtersResults() {
        customerService.create(new CreateCustomerRequest("Alpha Corp", "alpha@example.com", null, null));
        customerService.create(new CreateCustomerRequest("Beta Inc", "beta@example.com", null, null));

        PageResponse<CustomerResponse> page = customerService.findAll("Alpha", Pageable.unpaged());
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).name()).isEqualTo("Alpha Corp");
    }

}
