package com.lreyes.platform.core.authsecurity;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convierte un JWT a un {@link JwtAuthenticationToken} con las authorities correctas.
 * <p>
 * Soporta dos formatos de roles:
 * <ul>
 *   <li><b>Dev/Local</b>: claim {@code roles} → {@code ["admin", "gestor"]}</li>
 *   <li><b>Keycloak</b>: claim {@code realm_access.roles} → {@code ["admin", "gestor"]}</li>
 * </ul>
 * <p>
 * Los roles se convierten a {@code ROLE_admin}, {@code ROLE_gestor}, etc.
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // 1. Intentar claim "roles" directo (formato dev/local)
        List<String> roles = jwt.getClaimAsStringList("roles");

        // 2. Si no existe, intentar Keycloak format: realm_access.roles
        if (roles == null || roles.isEmpty()) {
            roles = extractKeycloakRoles(jwt);
        }

        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(RoleConstants.ROLE_PREFIX + role))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractKeycloakRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object rolesObj = map.get("roles");
            if (rolesObj instanceof List<?> list) {
                return (List<String>) list;
            }
        }
        return Collections.emptyList();
    }
}
