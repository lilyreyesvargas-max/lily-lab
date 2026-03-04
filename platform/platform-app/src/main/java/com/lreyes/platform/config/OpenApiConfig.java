package com.lreyes.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI 3 / Swagger UI.
 * <p>
 * Acceso:
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui/index.html}</li>
 *   <li>OpenAPI JSON: {@code /v3/api-docs}</li>
 *   <li>OpenAPI YAML: {@code /v3/api-docs.yaml}</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Platform API")
                        .description("API REST de la plataforma multi-tenant. "
                                + "Incluye módulos de clientes, empleados, ventas, documentos y workflow.")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("platform@lreyes.com"))
                        .license(new License()
                                .name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT obtenido de POST /api/auth/login (dev/local) "
                                                + "o del proveedor OIDC (prod)")));
    }
}
