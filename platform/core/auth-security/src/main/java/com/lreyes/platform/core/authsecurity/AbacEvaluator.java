package com.lreyes.platform.core.authsecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evaluador ABAC (Attribute-Based Access Control) — stub para V1.
 * <p>
 * Permite reglas basadas en atributos además de roles (RBAC).
 * Ejemplo: "un gestor solo puede aprobar ventas hasta $10,000 en su región".
 * <p>
 * Uso con {@code @PreAuthorize}:
 * <pre>
 * &#64;PreAuthorize("@abacEvaluator.canApprove(authentication, #amount, #region)")
 * public void approveOrder(BigDecimal amount, String region) { ... }
 * </pre>
 * <p>
 * En V1, las reglas están hardcodeadas. En V2, se pueden cargar desde BD o policy engine.
 */
@Component("abacEvaluator")
@Slf4j
public class AbacEvaluator {

    /** Monto máximo que un gestor puede aprobar sin escalamiento */
    private static final BigDecimal GESTOR_MAX_AMOUNT = new BigDecimal("10000");

    /**
     * Evalúa si el usuario autenticado puede aprobar un monto dado.
     *
     * @param auth   autenticación actual
     * @param amount monto a aprobar
     * @return true si está autorizado
     */
    public boolean canApprove(Authentication auth, BigDecimal amount) {
        if (hasRole(auth, RoleConstants.ADMIN)) {
            return true; // Admin aprueba cualquier monto
        }

        if (hasRole(auth, RoleConstants.GESTOR)) {
            boolean allowed = amount.compareTo(GESTOR_MAX_AMOUNT) <= 0;
            if (!allowed) {
                log.warn("Gestor '{}' intentó aprobar monto {} (máximo: {})",
                        auth.getName(), amount, GESTOR_MAX_AMOUNT);
            }
            return allowed;
        }

        return false;
    }

    /**
     * Evalúa si el usuario puede operar en una región dada.
     * Stub: en V1 todos pueden operar en cualquier región.
     */
    public boolean canOperateInRegion(Authentication auth, String region) {
        // V1: sin restricción por región. V2: leer de atributos del usuario.
        log.trace("ABAC region check: user='{}', region='{}' → permitido (stub)",
                auth.getName(), region);
        return true;
    }

    /**
     * Evaluación genérica por atributos.
     * Stub para V2: permitirá reglas dinámicas cargadas desde BD.
     */
    public boolean evaluate(Authentication auth, String resource, String action,
                            Map<String, Object> attributes) {
        log.trace("ABAC evaluate: user='{}', resource='{}', action='{}', attrs={}",
                auth.getName(), resource, action, attributes);
        // V1: delega a RBAC (siempre permite si está autenticado)
        return auth.isAuthenticated();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_PREFIX + role));
    }
}
