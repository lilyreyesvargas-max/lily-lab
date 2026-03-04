package com.lreyes.platform.core.authsecurity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Genera tokens JWT firmados con HS256 para los perfiles dev/local.
 * <p>
 * NO usar en producción — en prod los tokens los emite Keycloak (OIDC).
 * <p>
 * Claims generados:
 * <ul>
 *   <li>{@code sub}: username</li>
 *   <li>{@code tenant_id}: tenant del usuario</li>
 *   <li>{@code roles}: lista de roles</li>
 *   <li>{@code iat}: timestamp de emisión</li>
 *   <li>{@code exp}: timestamp de expiración</li>
 * </ul>
 */
@Slf4j
public class DevJwtService {

    private final JWSSigner signer;
    private final int expirationMinutes;

    public DevJwtService(String secret, int expirationMinutes) {
        try {
            byte[] secretBytes = secret.getBytes();
            if (secretBytes.length < 32) {
                throw new IllegalArgumentException(
                        "JWT secret debe tener al menos 32 caracteres (256 bits) para HS256");
            }
            this.signer = new MACSigner(secretBytes);
            this.expirationMinutes = expirationMinutes;
            log.info("DevJwtService inicializado (HS256, expiración: {} min)", expirationMinutes);
        } catch (JOSEException e) {
            throw new IllegalStateException("Error inicializando JWT signer", e);
        }
    }

    /**
     * Genera un token JWT para el usuario indicado.
     *
     * @param username  nombre de usuario
     * @param tenantId  tenant al que pertenece
     * @param roles     roles asignados
     * @return token JWT serializado
     */
    public String generateToken(String username, String tenantId, List<String> roles) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("tenant_id", tenantId)
                    .claim("roles", roles)
                    .issuer("platform-dev")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims);
            signedJWT.sign(signer);

            log.debug("Token generado para user='{}', tenant='{}', roles={}",
                    username, tenantId, roles);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Error generando JWT", e);
        }
    }
}
