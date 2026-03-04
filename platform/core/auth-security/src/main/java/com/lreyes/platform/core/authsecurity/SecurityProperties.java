package com.lreyes.platform.core.authsecurity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de seguridad.
 * <pre>
 * app:
 *   security:
 *     mode: jwt-local | oidc
 *     jwt-secret: clave-de-al-menos-256-bits
 *     token-expiration-minutes: 60
 * </pre>
 */
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class SecurityProperties {

    /** Modo de autenticación: "jwt-local" (dev/local) o "oidc" (prod con Keycloak) */
    private String mode = "jwt-local";

    /** Clave secreta para firmar JWT en modo jwt-local (mínimo 32 chars para HS256) */
    private String jwtSecret;

    /** Tiempo de expiración del token en minutos */
    private int tokenExpirationMinutes = 60;

    public boolean isJwtLocal() {
        return "jwt-local".equalsIgnoreCase(mode);
    }

    public boolean isOidc() {
        return "oidc".equalsIgnoreCase(mode);
    }
}
