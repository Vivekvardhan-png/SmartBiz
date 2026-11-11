package com.nexusops.platform.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security foundation — Prompt 00/01 placeholder.
 *
 * <p>IMPORTANT: This is a permissive bootstrap configuration that allows the
 * API to be exercised without authentication while the backend foundation is
 * established. Full JWT/session-based multi-tenant authentication will replace
 * this in <strong>Prompt 02</strong>.
 *
 * <p>Until Prompt 02 is applied:
 * <ul>
 *   <li>The health and OpenAPI endpoints are explicitly public.</li>
 *   <li>All other API requests are permitted (no auth check) so the
 *       frontend integration endpoint in this phase is reachable.</li>
 *   <li>CSRF is disabled because there are no session cookies yet.</li>
 * </ul>
 *
 * <p>Do <em>not</em> use this configuration in any environment that handles
 * real business data until Prompt 02 is complete.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {

        http
            // Apply our explicit CORS allowlist — not a wildcard.
            .cors(cors -> cors.configurationSource(corsSource))

            // CSRF disabled: no browser session cookies in Phase 00/01.
            // Will be re-evaluated in Prompt 02 based on selected auth approach.
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless for now; will be revisited in Prompt 02.
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                    // Health and readiness probes — always public.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                    // OpenAPI docs — dev environment only.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                    // Preflight — browsers send OPTIONS before credentialed requests.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // === TEMPORARY: permit all API traffic until Prompt 02 auth ===
                    // Replace this rule with authenticated-only access in Prompt 02.
                    .anyRequest().permitAll()
            );

        return http.build();
    }
}

