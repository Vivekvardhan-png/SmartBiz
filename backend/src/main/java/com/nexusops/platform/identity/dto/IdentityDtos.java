package com.nexusops.platform.identity.dto;

import com.nexusops.platform.identity.OrganizationRole;
import com.nexusops.platform.identity.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 10-128 characters and include uppercase, lowercase, and numeric characters.";

    public record RegisterRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @NotBlank(message = "Password is required.")
            @Size(min = 10, max = 128, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,

            @NotBlank(message = "Display name is required.")
            @Size(max = 255)
            String displayName,

            @NotBlank(message = "Organization name is required.")
            @Size(min = 2, max = 255)
            String organizationName,

            @Size(max = 100)
            @Pattern(
                    regexp = "^[a-z0-9-]*$",
                    message = "Organization slug may contain only lowercase letters, numbers, and hyphens."
            )
            String organizationSlug
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            String email,

            @NotBlank(message = "Password is required.")
            String password,

            UUID organizationId
    ) {}

    public record SwitchOrganizationRequest(
            @NotNull(message = "Target organizationId is required.")
            UUID organizationId
    ) {}

    public record CreateOrganizationRequest(
            @NotBlank(message = "Organization name is required.")
            @Size(min = 2, max = 255)
            String name,

            @Size(max = 100)
            @Pattern(
                    regexp = "^[a-z0-9-]*$",
                    message = "Organization slug may contain only lowercase letters, numbers, and hyphens."
            )
            String slug
    ) {}

    public record AddMemberRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 255)
            String displayName,

            @Size(min = 10, max = 128, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,

            @NotNull(message = "Role is required.")
            OrganizationRole role
    ) {}

    public record UpdateMemberRoleRequest(
            @NotNull(message = "Role is required.")
            OrganizationRole role
    ) {}

    public record UserSummary(
            UUID id,
            String email,
            String displayName
    ) {}

    public record OrganizationSummary(
            UUID id,
            String name,
            String slug,
            OrganizationRole role
    ) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            UserSummary user,
            OrganizationSummary activeOrganization,
            Set<Permission> permissions,
            List<OrganizationSummary> organizations
    ) {}

    public record SessionInfoResponse(
            UserSummary user,
            OrganizationSummary activeOrganization,
            Set<Permission> permissions,
            List<OrganizationSummary> organizations
    ) {}

    public record OrganizationMemberResponse(
            UUID id,
            UUID userId,
            String email,
            String displayName,
            OrganizationRole role,
            Instant joinedAt
    ) {}
}

