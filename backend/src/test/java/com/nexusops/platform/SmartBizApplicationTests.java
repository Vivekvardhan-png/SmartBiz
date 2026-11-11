package com.nexusops.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context loads correctly against the
 * H2 test datasource (Flyway disabled; Hibernate create-drop).
 */
@SpringBootTest
@ActiveProfiles("test")
class SmartBizApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to load, this test reports the cause clearly.
    }
}

