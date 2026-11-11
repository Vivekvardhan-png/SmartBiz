package com.nexusops.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexusOps Platform — AI-integrated CRM and ERP for small and medium businesses.
 *
 * <p>Modular monolith: business logic is organized into domain packages under
 * {@code com.nexusops.platform}. No microservices. Cross-cutting concerns
 * (security, error handling, API conventions) live in {@code common}.
 *
 * <p>Stack: Spring Boot 3.3, Java 21 (compiled on Java 25), PostgreSQL,
 * Flyway, Spring Data JPA, Spring Security, Springdoc OpenAPI.
 */
@SpringBootApplication
public class NexusOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusOpsApplication.class, args);
    }
}

