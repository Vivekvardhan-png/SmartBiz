package com.nexusops.platform.identity;

import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.identity.dto.IdentityDtos.AuthResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.LoginRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.OrganizationSummary;
import com.nexusops.platform.identity.dto.IdentityDtos.RegisterRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.SessionInfoResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.SwitchOrganizationRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.UserSummary;
import com.nexusops.platform.security.JwtService;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles registration onboarding, credential verification, session issuance,
 * organization switching, and session revocation.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            AuthSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        String slug = resolveUniqueSlug(request.organizationSlug(), request.organizationName());
        Organization organization = organizationRepository.save(
                new Organization(request.organizationName().trim(), slug));

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                new User(email, request.displayName().trim(), passwordHash));

        OrganizationMember membership = memberRepository.save(
                new OrganizationMember(organization, user, OrganizationRole.OWNER));

        return issueSessionResponse(user, membership, List.of(toSummary(membership)));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        List<OrganizationMember> memberships = memberRepository.findAllWithOrganizationByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new AccessDeniedException("User does not belong to any organization.");
        }

        OrganizationMember activeMembership;
        if (request.organizationId() != null) {
            activeMembership = memberships.stream()
                    .filter(m -> m.getOrganization().getId().equals(request.organizationId()))
                    .findFirst()
                    .orElseThrow(() -> new AccessDeniedException(
                            "User is not a member of the requested organization."));
        } else {
            activeMembership = memberships.getFirst();
        }

        List<OrganizationSummary> orgSummaries = memberships.stream()
                .map(AuthService::toSummary)
                .toList();

        return issueSessionResponse(user, activeMembership, orgSummaries);
    }

    @Transactional
    public AuthResponse switchOrganization(TenantPrincipal principal, SwitchOrganizationRequest request) {
        OrganizationMember targetMembership = memberRepository
                .findWithDetailsByOrganizationIdAndUserId(request.organizationId(), principal.userId())
                .orElseThrow(() -> new AccessDeniedException(
                        "You are not a member of the requested organization."));

        sessionRepository.findByIdAndRevokedAtIsNull(principal.sessionId())
                .ifPresent(session -> session.revoke(Instant.now()));

        List<OrganizationSummary> orgSummaries = memberRepository
                .findAllWithOrganizationByUserId(principal.userId())
                .stream()
                .map(AuthService::toSummary)
                .toList();

        return issueSessionResponse(targetMembership.getUser(), targetMembership, orgSummaries);
    }

    @Transactional
    public void logout(TenantPrincipal principal) {
        sessionRepository.findByIdAndRevokedAtIsNull(principal.sessionId())
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public SessionInfoResponse currentSession(TenantPrincipal principal) {
        List<OrganizationSummary> organizations = memberRepository
                .findAllWithOrganizationByUserId(principal.userId())
                .stream()
                .map(AuthService::toSummary)
                .toList();

        return new SessionInfoResponse(
                new UserSummary(principal.userId(), principal.email(), principal.displayName()),
                new OrganizationSummary(
                        principal.organizationId(),
                        principal.organizationName(),
                        principal.organizationSlug(),
                        principal.role()),
                principal.permissions(),
                organizations
        );
    }

    private AuthResponse issueSessionResponse(
            User user,
            OrganizationMember activeMembership,
            List<OrganizationSummary> organizations) {

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtService.getExpirationSeconds());

        AuthSession session = sessionRepository.save(
                new AuthSession(user.getId(), activeMembership.getOrganization().getId(), expiresAt));

        String accessToken = jwtService.issueAccessToken(
                user.getId(),
                session.getId(),
                activeMembership.getOrganization().getId(),
                now);

        return new AuthResponse(
                accessToken,
                "Bearer",
                expiresAt,
                new UserSummary(user.getId(), user.getEmail(), user.getDisplayName()),
                toSummary(activeMembership),
                activeMembership.getRole().permissions(),
                organizations
        );
    }

    private String resolveUniqueSlug(String requestedSlug, String organizationName) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String clean = SlugUtil.toSlug(requestedSlug);
            if (organizationRepository.existsBySlug(clean)) {
                throw new ConflictException("Organization slug '" + clean + "' is already taken.");
            }
            return clean;
        }

        String base = SlugUtil.toSlug(organizationName);
        String candidate = base;
        int attempt = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + (++attempt);
        }
        return candidate;
    }

    static String normalizeEmail(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    static OrganizationSummary toSummary(OrganizationMember member) {
        return new OrganizationSummary(
                member.getOrganization().getId(),
                member.getOrganization().getName(),
                member.getOrganization().getSlug(),
                member.getRole()
        );
    }
}

