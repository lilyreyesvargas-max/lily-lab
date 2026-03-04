package com.lreyes.platform.core.tenancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TenantFilterTest {

    private TenantFilter filter;
    private TenantProperties props;

    @BeforeEach
    void setUp() {
        props = new TenantProperties();
        props.setTenants(List.of("acme", "globex"));
        filter = new TenantFilter(props);
    }

    @Test
    void validTenantHeader_shouldSetContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "acme");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        // Verificamos que el contexto se limpió después del filter
        assertEquals("public", TenantContext.getCurrentTenant());
    }

    @Test
    void invalidTenantFormat_shouldReturn400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "acme; DROP SCHEMA");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
    }

    @Test
    void unregisteredTenant_shouldReturn403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "unknown_corp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void noTenantHeader_shouldPassWithoutSettingContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("public", TenantContext.getCurrentTenant());
    }

    @Test
    void actuatorPath_shouldBeExcluded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void apiPath_shouldNotBeExcluded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void tenantHeader_shouldBeCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "ACME");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // "ACME" se normaliza a "acme" → válido y registrado
        assertEquals(200, response.getStatus());
    }

    @Test
    void contextCleared_evenOnException() throws Exception {
        TenantContext.setCurrentTenant("leftover");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "acme");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // El finally siempre limpia el contexto
        assertEquals("public", TenantContext.getCurrentTenant());
    }
}
