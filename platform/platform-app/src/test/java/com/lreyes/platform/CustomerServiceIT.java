package com.lreyes.platform;

import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.customers.CustomerServicePort;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestPlatformJdbcConfig.class)
@Transactional
class CustomerServiceIT {

    @Autowired
    private CustomerServicePort customerService;

    @BeforeTransaction
    void beforeTransaction() {
        TenantContext.setCurrentTenant("test");
    }

    @AfterTransaction
    void afterTransaction() {
        TenantContext.clear();
    }

    @Test
    void createAndFind() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("Acme IT", "acme@test.com", null, null));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Acme IT");

        CustomerResponse found = customerService.findById(created.id());
        assertThat(found.name()).isEqualTo("Acme IT");
        assertThat(found.email()).isEqualTo("acme@test.com");
    }

    @Test
    void updateCustomer() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("Before Update", "before@test.com", null, null));

        customerService.update(created.id(),
                new UpdateCustomerRequest("After Update", null, null, null, null));

        CustomerResponse updated = customerService.findById(created.id());
        assertThat(updated.name()).isEqualTo("After Update");
    }

    @Test
    void deleteCustomer() {
        CustomerResponse created = customerService.create(
                new CreateCustomerRequest("To Delete", "delete@test.com", null, null));
        long countBefore = customerService.findAll(null, Pageable.unpaged()).totalElements();

        customerService.delete(created.id());

        long countAfter = customerService.findAll(null, Pageable.unpaged()).totalElements();
        assertThat(countAfter).isEqualTo(countBefore - 1);
        assertThatThrownBy(() -> customerService.findById(created.id()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findAll_returnsPageResponse() {
        customerService.create(new CreateCustomerRequest("Customer A", "a@test.com", null, null));
        customerService.create(new CreateCustomerRequest("Customer B", "b@test.com", null, null));

        PageResponse<CustomerResponse> page = customerService.findAll(null, Pageable.unpaged());
        assertThat(page.content()).hasSizeGreaterThanOrEqualTo(2);
    }
}
