package com.nexusops.platform.common.exception;

import com.nexusops.platform.config.SecurityConfig;
import com.nexusops.platform.security.JwtAuthenticationFilter;
import com.nexusops.platform.security.RestAccessDeniedHandler;
import com.nexusops.platform.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for GlobalExceptionHandler.
 *
 * Uses @WebMvcTest (the correct pattern for exception-handler testing):
 *  - @WebMvcTest without controllers= auto-detects all @RestControllerAdvice beans
 *    (including GlobalExceptionHandler) from the main source set.
 *  - TestExceptionController is explicitly @Import-ed since it lives in the test
 *    source set and is therefore not reachable by @WebMvcTest's component scan.
 *  - Spring Security is excluded so business-level exceptions are not intercepted
 *    by the security filter chain before reaching the DispatcherServlet.
 *  - Faster than @SpringBootTest — no JPA, Flyway, or DataSource required.
 */
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestExceptionController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        SecurityConfig.class,
                        JwtAuthenticationFilter.class,
                        RestAuthenticationEntryPoint.class,
                        RestAccessDeniedHandler.class
                }
        )
)
@Import({
        GlobalExceptionHandlerTest.TestExceptionController.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        // Allow NoHandlerFoundException to propagate so our handler can map it.
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // Minimal controller that intentionally throws each domain exception.
    // Registered via @WebMvcTest(controllers = ...) above.
    // -------------------------------------------------------------------------
    @RestController
    @RequestMapping("/test/exception-handler")
    static class TestExceptionController {

        @GetMapping("/not-found")
        void throwNotFound() {
            throw new ResourceNotFoundException("Widget", "abc-123");
        }

        @GetMapping("/conflict")
        void throwConflict() {
            throw new ConflictException("A widget with this name already exists.");
        }

        @GetMapping("/forbidden")
        void throwForbidden() {
            throw new AccessDeniedException();
        }

        @GetMapping("/business-rule")
        void throwBusinessRule() {
            throw new BusinessRuleException("Lead has already been converted.");
        }

        @PostMapping("/validate")
        void validateBody(@Valid @RequestBody ValidatedPayload payload) {
            // Returns 200 on success; validation failure triggers
            // MethodArgumentNotValidException → GlobalExceptionHandler.
        }

        record ValidatedPayload(
                @NotBlank(message = "Name must not be blank") String name,
                @Size(min = 3, message = "Code must be at least 3 characters") String code
        ) {}
    }

    // =========================================================================
    // Domain exception tests
    // =========================================================================

    @Test
    @DisplayName("404 Not Found — ResourceNotFoundException produces ErrorResponse")
    void notFoundProducesErrorResponse() throws Exception {
        mockMvc.perform(get("/test/exception-handler/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Widget with id 'abc-123' was not found."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("409 Conflict — ConflictException produces ErrorResponse")
    void conflictProducesErrorResponse() throws Exception {
        mockMvc.perform(get("/test/exception-handler/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("403 Forbidden — AccessDeniedException produces ErrorResponse")
    void forbiddenProducesErrorResponse() throws Exception {
        mockMvc.perform(get("/test/exception-handler/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("422 Unprocessable Entity — BusinessRuleException produces ErrorResponse")
    void businessRuleProducesErrorResponse() throws Exception {
        mockMvc.perform(get("/test/exception-handler/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(422));
    }

    // =========================================================================
    // Validation and HTTP protocol tests
    // =========================================================================

    @Test
    @DisplayName("400 Bad Request — @Valid failure returns field-level errors")
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/exception-handler/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("400 Bad Request — malformed JSON returns a safe message, no stack trace")
    void malformedBodyReturnsSafeMessage() throws Exception {
        mockMvc.perform(post("/test/exception-handler/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("NOT VALID JSON {"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value("Request body is malformed or missing required fields."))
                // Stack trace must never appear in the response body.
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("404 Not Found — unknown route returns JSON ErrorResponse, not Spring whitelabel")
    void unknownRouteReturnsJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist-route-999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
