package com.lreyes.platform.core.authsecurity;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevJwtServiceTest {

    private static final String SECRET = "test-secret-key-minimum-256-bits-long-for-hs256-signing-00";

    private DevJwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new DevJwtService(SECRET, 60);
    }

    @Test
    void generateToken_shouldProduceValidJwt() throws ParseException {
        String token = jwtService.generateToken("admin", "acme", List.of("admin", "gestor"));

        assertNotNull(token);
        assertFalse(token.isBlank());

        // Parsear el JWT
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertEquals("admin", claims.getSubject());
        assertEquals("acme", claims.getStringClaim("tenant_id"));
        assertEquals(List.of("admin", "gestor"), claims.getStringListClaim("roles"));
        assertEquals("platform-dev", claims.getIssuer());
        assertNotNull(claims.getIssueTime());
        assertNotNull(claims.getExpirationTime());
        assertTrue(claims.getExpirationTime().after(new Date()));
    }

    @Test
    void generateToken_shouldHaveCorrectExpiration() throws ParseException {
        String token = jwtService.generateToken("user", "globex", List.of("operador"));
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        long diffMs = claims.getExpirationTime().getTime() - claims.getIssueTime().getTime();
        long diffMinutes = diffMs / 60000;

        assertEquals(60, diffMinutes, 1); // 60 min ± 1 min de tolerancia
    }

    @Test
    void shortSecret_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> new DevJwtService("short", 60));
    }
}
