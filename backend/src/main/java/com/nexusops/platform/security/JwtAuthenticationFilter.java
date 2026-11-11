package com.nexusops.platform.security;

import com.nexusops.platform.identity.AuthSession;
import com.nexusops.platform.identity.AuthSessionRepository;
import com.nexusops.platform.identity.OrganizationMember;
import com.nexusops.platform.identity.OrganizationMemberRepository;
import com.nexusops.platform.identity.Permission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates Bearer JWTs against persisted {@link AuthSession} and live
 * {@link OrganizationMember} records so revoked sessions or removed members
 * lose access immediately.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthSessionRepository authSessionRepository;
    private final OrganizationMemberRepository memberRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthSessionRepository authSessionRepository,
            OrganizationMemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.authSessionRepository = authSessionRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtService.ParsedToken parsed = jwtService.parseAndVerify(token);
            Instant now = Instant.now();

            Optional<AuthSession> sessionOpt = authSessionRepository.findByIdAndRevokedAtIsNull(parsed.sessionId());
            if (sessionOpt.isEmpty()) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            AuthSession session = sessionOpt.get();
            if (!session.isActiveAt(now)
                    || !session.getUserId().equals(parsed.userId())
                    || !session.getOrganizationId().equals(parsed.organizationId())) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            Optional<OrganizationMember> memberOpt = memberRepository
                    .findWithDetailsByOrganizationIdAndUserId(
                            session.getOrganizationId(),
                            session.getUserId());

            if (memberOpt.isEmpty() || !memberOpt.get().getUser().isEnabled()) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            OrganizationMember member = memberOpt.get();
            Set<Permission> permissions = member.getRole().permissions();
            TenantPrincipal principal = new TenantPrincipal(
                    member.getUser().getId(),
                    member.getUser().getEmail(),
                    member.getUser().getDisplayName(),
                    session.getId(),
                    member.getOrganization().getId(),
                    member.getOrganization().getName(),
                    member.getOrganization().getSlug(),
                    member.getRole(),
                    permissions
            );

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
            for (Permission permission : permissions) {
                authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name()));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtService.InvalidTokenException ex) {
            log.debug("Rejected invalid JWT token for {} {}", request.getMethod(), request.getRequestURI());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

