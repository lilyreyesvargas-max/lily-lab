package com.lreyes.platform.core.authsecurity;

import com.lreyes.platform.core.authsecurity.dto.LoginRequest;
import com.lreyes.platform.core.authsecurity.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint de autenticación para perfiles dev/local.
 * <p>
 * Genera un JWT firmado con HS256 vía {@link DevJwtService}.
 * Este controller solo existe cuando {@code DevJwtService} está activo
 * (modo {@code jwt-local}). En producción (OIDC) no se registra.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnBean(DevJwtService.class)
@Slf4j
public class AuthController {

    private final DevJwtService devJwtService;
    private final SecurityProperties securityProperties;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        List<String> roles = (request.roles() != null && !request.roles().isEmpty())
                ? request.roles()
                : List.of("ROLE_USER");

        String token = devJwtService.generateToken(
                request.username(),
                request.tenantId(),
                roles);

        log.info("Login dev: user='{}', tenant='{}', roles={}", request.username(), request.tenantId(), roles);

        return ResponseEntity.ok(new LoginResponse(
                token,
                request.username(),
                request.tenantId(),
                roles,
                securityProperties.getTokenExpirationMinutes() * 60L));
    }
}
