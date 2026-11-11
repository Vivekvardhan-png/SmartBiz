package com.nexusops.platform.identity;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.AuthResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.LoginRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.RegisterRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.SessionInfoResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.SwitchOrganizationRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Onboarding, login, session inspection, organization switching, and logout.")
public class AuthController {

    private final AuthService authService;
    private final TenantContext tenantContext;

    public AuthController(AuthService authService, TenantContext tenantContext) {
        this.authService = authService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/register")
    @Operation(summary = "Register owner account and initial organization")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/switch-organization")
    @Operation(summary = "Switch active organization for an authenticated user")
    public ResponseEntity<ApiResponse<AuthResponse>> switchOrganization(
            @Valid @RequestBody SwitchOrganizationRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                authService.switchOrganization(tenantContext.requirePrincipal(), request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke current authentication session")
    public ResponseEntity<Void> logout() {
        authService.logout(tenantContext.requirePrincipal());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user, active organization, and permissions")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> me() {
        return ResponseEntity.ok(ApiResponse.of(
                authService.currentSession(tenantContext.requirePrincipal())));
    }
}

