package com.lreyes.platform.core.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro web que extrae el tenant del request y lo pone en {@link TenantContext}.
 * <p>
 * Orden de resolución:
 * <ol>
 *   <li>Header {@code X-Tenant-Id}</li>
 *   <li>(futuro) JWT claim {@code tenant_id}</li>
 * </ol>
 * <p>
 * Si no se encuentra tenant, las rutas públicas (actuator, login, swagger) pasan sin tenant.
 * Las rutas protegidas fallarán con 400 si no hay tenant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantProperties tenantProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolveTenant(request);

            if (tenantId != null) {
                if (!TenantProperties.isValidTenantName(tenantId)) {
                    log.warn("Tenant ID con formato inválido: '{}'", tenantId);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Tenant ID inválido: solo letras minúsculas, números y guión bajo");
                    return;
                }

                if (!tenantProperties.isRegisteredTenant(tenantId)) {
                    log.warn("Tenant no registrado: '{}'", tenantId);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Tenant no registrado");
                    return;
                }

                TenantContext.setCurrentTenant(tenantId);
                log.debug("Tenant establecido: {}", tenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console")
                || path.startsWith("/zul")
                || path.startsWith("/zkau")
                || path.endsWith(".zul");
    }

    private String resolveTenant(HttpServletRequest request) {
        // 1. Header X-Tenant-Id
        String header = request.getHeader(TENANT_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim().toLowerCase();
        }

        // 2. (futuro) JWT claim tenant_id — se implementa en Paso 5

        return null;
    }
}
