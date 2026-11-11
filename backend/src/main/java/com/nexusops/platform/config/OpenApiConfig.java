package com.nexusops.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI / Swagger UI configuration.
 *
 * <p>Swagger UI is accessible at {@code /swagger-ui.html} in the local dev environment.
 * The OpenAPI JSON spec is served at {@code /v3/api-docs}.
 * Both endpoints are permitted without authentication (see SecurityConfig).
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:NexusOps Platform}")
    private String applicationName;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("""
                                REST API for the NexusOps CRM and ERP platform.
                                All business endpoints require authentication (implemented in Prompt 02).
                                Organization-scoped data isolation is enforced server-side.
                                """)
                        .version("0.1.0")
                        .contact(new Contact().name("NexusOps Engineering"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")));
    }
}

