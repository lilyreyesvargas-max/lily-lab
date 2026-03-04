package com.lreyes.platform.core.authsecurity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthConverterTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();

    @Test
    void shouldExtractDevRoles() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "admin",
                "roles", List.of("admin", "gestor")
        ));

        JwtAuthenticationToken auth = (JwtAuthenticationToken) converter.convert(jwt);

        assertNotNull(auth);
        assertEquals("admin", auth.getName());

        Collection<GrantedAuthority> authorities = auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_gestor")));
    }

    @Test
    void shouldExtractKeycloakRoles() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "kc-user",
                "realm_access", Map.of("roles", List.of("operador"))
        ));

        JwtAuthenticationToken auth = (JwtAuthenticationToken) converter.convert(jwt);

        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_operador")));
    }

    @Test
    void noRoles_shouldReturnEmptyAuthorities() {
        Jwt jwt = buildJwt(Map.of("sub", "minimal-user"));

        JwtAuthenticationToken auth = (JwtAuthenticationToken) converter.convert(jwt);

        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());
    }

    @Test
    void devRolesTakePrecedenceOverKeycloak() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "dual-user",
                "roles", List.of("admin"),
                "realm_access", Map.of("roles", List.of("operador"))
        ));

        JwtAuthenticationToken auth = (JwtAuthenticationToken) converter.convert(jwt);

        // "roles" claim takes precedence → only admin
        assertEquals(1, auth.getAuthorities().size());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_admin")));
    }

    private Jwt buildJwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("dummy-token")
                .header("alg", "HS256")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
