package com.lreyes.platform.core.authsecurity;

import com.lreyes.platform.core.authsecurity.dto.LoginRequest;
import com.lreyes.platform.core.authsecurity.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private DevJwtService devJwtService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setTokenExpirationMinutes(60);
        authController = new AuthController(devJwtService, props);
    }

    @Test
    @DisplayName("login - genera token con roles proporcionados")
    void login_withRoles_generatesToken() {
        var request = new LoginRequest("admin", "acme", List.of("ROLE_ADMIN", "ROLE_USER"));
        when(devJwtService.generateToken(eq("admin"), eq("acme"), eq(List.of("ROLE_ADMIN", "ROLE_USER"))))
                .thenReturn("jwt-token-123");

        ResponseEntity<LoginResponse> resp = authController.login(request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().token()).isEqualTo("jwt-token-123");
        assertThat(resp.getBody().username()).isEqualTo("admin");
        assertThat(resp.getBody().tenantId()).isEqualTo("acme");
        assertThat(resp.getBody().roles()).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(resp.getBody().expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("login - sin roles usa ROLE_USER por defecto")
    void login_noRoles_usesDefault() {
        var request = new LoginRequest("user1", "globex", null);
        when(devJwtService.generateToken(eq("user1"), eq("globex"), eq(List.of("ROLE_USER"))))
                .thenReturn("jwt-token-default");

        ResponseEntity<LoginResponse> resp = authController.login(request);

        assertThat(resp.getBody().roles()).containsExactly("ROLE_USER");
        verify(devJwtService).generateToken("user1", "globex", List.of("ROLE_USER"));
    }

    @Test
    @DisplayName("login - roles vacíos usa ROLE_USER por defecto")
    void login_emptyRoles_usesDefault() {
        var request = new LoginRequest("user2", "acme", List.of());
        when(devJwtService.generateToken(eq("user2"), eq("acme"), eq(List.of("ROLE_USER"))))
                .thenReturn("jwt-token-empty");

        ResponseEntity<LoginResponse> resp = authController.login(request);

        assertThat(resp.getBody().roles()).containsExactly("ROLE_USER");
    }
}
