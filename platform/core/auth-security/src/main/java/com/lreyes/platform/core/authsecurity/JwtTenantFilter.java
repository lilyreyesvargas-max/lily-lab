package com.lreyes.platform.core.authsecurity;

import com.lreyes.platform.core.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que extrae {@code tenant_id} del JWT y lo pone en {@link TenantContext}
 * si no fue establecido previamente por el header {@code X-Tenant-Id}.
 * <p>
 * Se registra DESPUÉS de Spring Security (dentro del SecurityFilterChain)
 * para que el JWT ya esté parseado en el SecurityContext.
 * <p>
 * Prioridad:
 * <ol>
 *   <li>Header {@code X-Tenant-Id} (establecido por TenantFilter, que corre antes)</li>
 *   <li>JWT claim {@code tenant_id} (este filtro)</li>
 * </ol>
 */
@Slf4j
public class JwtTenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Solo actuar si TenantContext no fue establecido por X-Tenant-Id header
        if (TenantContext.getCurrentTenantOptional().isEmpty()) {
            String tenantFromJwt = extractTenantFromJwt();
            if (tenantFromJwt != null) {
                TenantContext.setCurrentTenant(tenantFromJwt);
                log.debug("Tenant establecido desde JWT claim: {}", tenantFromJwt);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTenantFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId.trim().toLowerCase();
            }
        }
        return null;
    }
}
