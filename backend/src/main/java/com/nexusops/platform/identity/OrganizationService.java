package com.nexusops.platform.identity;

import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.identity.dto.IdentityDtos.AddMemberRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.CreateOrganizationRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.OrganizationMemberResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.OrganizationSummary;
import com.nexusops.platform.identity.dto.IdentityDtos.UpdateMemberRoleRequest;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Organization and membership management strictly scoped to the caller's
 * server-verified {@link TenantPrincipal#organizationId()}.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            AuthSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public OrganizationSummary getCurrentOrganization(TenantPrincipal principal) {
        Organization org = organizationRepository.findById(principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization", principal.organizationId()));
        return new OrganizationSummary(org.getId(), org.getName(), org.getSlug(), principal.role());
    }

    @Transactional
    public OrganizationSummary createOrganization(
            TenantPrincipal principal,
            CreateOrganizationRequest request) {
        User owner = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()));

        String slug = resolveUniqueSlug(request.slug(), request.name());
        Organization organization = organizationRepository.save(
                new Organization(request.name().trim(), slug));

        OrganizationMember membership = memberRepository.save(
                new OrganizationMember(organization, owner, OrganizationRole.OWNER));

        return AuthService.toSummary(membership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> listMembers(TenantPrincipal principal) {
        return memberRepository.findAllWithUserByOrganizationId(principal.organizationId())
                .stream()
                .map(OrganizationService::toMemberResponse)
                .toList();
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            TenantPrincipal principal,
            AddMemberRequest request) {
        if (request.role() == OrganizationRole.OWNER && principal.role() != OrganizationRole.OWNER) {
            throw new AccessDeniedException("Only an organization OWNER can assign the OWNER role.");
        }

        Organization organization = organizationRepository.findById(principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization", principal.organizationId()));

        String email = AuthService.normalizeEmail(request.email());
        User targetUser = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (targetUser == null) {
            if (request.password() == null || request.password().isBlank()) {
                throw new BusinessRuleException(
                        "A valid password is required when provisioning a new user account.");
            }
            String displayName = request.displayName() != null && !request.displayName().isBlank()
                    ? request.displayName().trim()
                    : email;
            targetUser = userRepository.save(
                    new User(email, displayName, passwordEncoder.encode(request.password())));
        } else if (memberRepository.existsByOrganizationIdAndUserId(organization.getId(), targetUser.getId())) {
            throw new ConflictException("User is already a member of this organization.");
        }

        OrganizationMember created = memberRepository.save(
                new OrganizationMember(organization, targetUser, request.role()));
        return toMemberResponse(created);
    }

    @Transactional
    public OrganizationMemberResponse updateMemberRole(
            TenantPrincipal principal,
            UUID memberId,
            UpdateMemberRoleRequest request) {
        OrganizationMember member = memberRepository
                .findByIdAndOrganizationId(memberId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", memberId));

        OrganizationRole nextRole = request.role();
        if ((member.getRole() == OrganizationRole.OWNER || nextRole == OrganizationRole.OWNER)
                && principal.role() != OrganizationRole.OWNER) {
            throw new AccessDeniedException("Only an organization OWNER can modify OWNER roles.");
        }

        if (member.getRole() == OrganizationRole.OWNER && nextRole != OrganizationRole.OWNER) {
            long ownerCount = memberRepository.countByOrganizationIdAndRole(
                    principal.organizationId(), OrganizationRole.OWNER);
            if (ownerCount <= 1) {
                throw new BusinessRuleException(
                        "An organization must retain at least one OWNER.");
            }
        }

        member.setRole(nextRole);
        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(TenantPrincipal principal, UUID memberId) {
        OrganizationMember member = memberRepository
                .findByIdAndOrganizationId(memberId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", memberId));

        if (member.getRole() == OrganizationRole.OWNER) {
            if (principal.role() != OrganizationRole.OWNER) {
                throw new AccessDeniedException("Only an organization OWNER can remove another OWNER.");
            }
            long ownerCount = memberRepository.countByOrganizationIdAndRole(
                    principal.organizationId(), OrganizationRole.OWNER);
            if (ownerCount <= 1) {
                throw new BusinessRuleException(
                        "Cannot remove the last OWNER of an organization.");
            }
        }

        sessionRepository.revokeActiveSessionsForMembership(
                member.getUser().getId(),
                principal.organizationId(),
                Instant.now());
        memberRepository.delete(member);
    }

    private String resolveUniqueSlug(String requestedSlug, String name) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String clean = SlugUtil.toSlug(requestedSlug);
            if (organizationRepository.existsBySlug(clean)) {
                throw new ConflictException("Organization slug '" + clean + "' is already taken.");
            }
            return clean;
        }
        String base = SlugUtil.toSlug(name);
        String candidate = base;
        int attempt = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + (++attempt);
        }
        return candidate;
    }

    private static OrganizationMemberResponse toMemberResponse(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}

