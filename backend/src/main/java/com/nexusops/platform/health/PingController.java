package com.nexusops.platform.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight application-level ping endpoint used by the frontend to verify
 * backend connectivity during development.
 *
 * <p>Does <em>not</em> expose database state, server details, stack traces,
 * memory metrics, or environment secrets.
 *
 * <p>For full infrastructure health (DB liveness, disk space) use the
 * Spring Boot Actuator endpoint at {@code GET /actuator/health}, which is
 * scoped and configured separately in {@code application.yml}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform", description = "Platform connectivity and information endpoints.")
public class PingController {

    /**
     * Verifies the Java backend is running and reachable from the frontend.
     *
     * @return {@code 200 OK} with a small JSON body confirming status and UTC timestamp.
     */
    @GetMapping("/ping")
    @Operation(
            summary = "Ping",
            description = "Returns a 200 OK with status 'UP' when the application is running. "
                    + "No authentication required. Does not expose internal system details."
    )
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "SmartBiz",
                "timestamp", Instant.now().toString()
        ));
    }
}

