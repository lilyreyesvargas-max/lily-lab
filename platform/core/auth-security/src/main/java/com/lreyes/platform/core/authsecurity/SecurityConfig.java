package com.lreyes.platform.core.authsecurity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Configuración central de Spring Security.
 * <p>
 * Características:
 * <ul>
 *   <li>Sesiones IF_REQUIRED: REST API usa JWT (no crea sesión); ZK UI usa sesión HTTP</li>
 *   <li>JWT como bearer token (OAuth2 Resource Server)</li>
 *   <li>Rutas públicas: auth, actuator, swagger, h2-console, ZK UI</li>
 *   <li>RBAC con anotaciones {@code @PreAuthorize} habilitadas</li>
 *   <li>{@link JwtTenantFilter} se inserta después de autenticación JWT</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    /**
     * Cadena de seguridad para ZK UI — sin OAuth2/JWT.
     * ZK registra sus propios servlets (DHtmlLayoutServlet, DHtmlUpdateServlet)
     * fuera de DispatcherServlet, por lo que necesita su propia cadena.
     * Usa sesiones HTTP para autenticación (manejada por LoginVM).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain zkFilterChain(HttpSecurity http) throws Exception {
        http
                // Usamos OrRequestMatcher con AntPathRequestMatcher porque
                // securityMatcher(String...) usa MvcRequestMatcher que no funciona
                // con servlets fuera de DispatcherServlet (como DHtmlLayoutServlet).
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/zul/**"),
                        new AntPathRequestMatcher("/zkau/**"),
                        new AntPathRequestMatcher("/login"),
                        new AntPathRequestMatcher("/")
                ))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        log.info("ZK SecurityFilterChain configurado (permitAll para ZK UI)");
        return http.build();
    }

    /**
     * Cadena de seguridad para REST API — con OAuth2 JWT.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .frameOptions(fo -> fo.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/logos/**",
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/h2-console/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/platform/**")
                        .hasRole(RoleConstants.PLATFORM_ADMIN)
                        .requestMatchers("/api/admin/**")
                        .hasRole(RoleConstants.ADMIN)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthConverter())
                        )
                )
                .addFilterAfter(jwtTenantFilter(), BearerTokenAuthenticationFilter.class);

        log.info("API SecurityFilterChain configurado (modo: {})", securityProperties.getMode());
        return http.build();
    }

    @Bean
    public JwtAuthConverter jwtAuthConverter() {
        return new JwtAuthConverter();
    }

    @Bean
    public JwtTenantFilter jwtTenantFilter() {
        return new JwtTenantFilter();
    }

    /**
     * JwtDecoder para modo jwt-local (dev/local): decodifica JWT con clave simétrica HS256.
     * <p>
     * En modo OIDC (prod), Spring Boot auto-configura el JwtDecoder desde
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (securityProperties.isJwtLocal()) {
            log.info("JwtDecoder configurado con clave simétrica HS256 (modo jwt-local)");
            SecretKey key = new SecretKeySpec(
                    securityProperties.getJwtSecret().getBytes(),
                    "HmacSHA256"
            );
            return NimbusJwtDecoder.withSecretKey(key).build();
        }

        // En modo OIDC, Spring Boot auto-configura el decoder.
        // Este bean no debería llegar aquí si OIDC está bien configurado,
        // pero como fallback lanzamos excepción clara.
        throw new IllegalStateException(
                "En modo OIDC, configure spring.security.oauth2.resourceserver.jwt.issuer-uri. "
                        + "No se necesita un JwtDecoder manual.");
    }

    /**
     * DevJwtService solo para generar tokens en dev/local.
     * En prod (OIDC) este bean NO se crea.
     */
    @Bean
    public DevJwtService devJwtService() {
        if (!securityProperties.isJwtLocal()) {
            return null;
        }
        return new DevJwtService(
                securityProperties.getJwtSecret(),
                securityProperties.getTokenExpirationMinutes()
        );
    }
}
