package com.lreyes.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("Genera requestId si no viene en header")
    void generatesRequestId_whenMissing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/customers");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).hasSize(8);
        // MDC se limpia después del filtro
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Usa requestId del header si viene proporcionado")
    void usesProvidedRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/employees");
        request.addHeader("X-Request-Id", "custom-id-123");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("custom-id-123");
    }

    @Test
    @DisplayName("No filtra rutas de ZK AJAX")
    void shouldNotFilter_zkauPaths() {
        var request = new MockHttpServletRequest("POST", "/zkau/upload");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Filtra rutas API normales")
    void shouldFilter_apiPaths() {
        var request = new MockHttpServletRequest("GET", "/api/customers");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
