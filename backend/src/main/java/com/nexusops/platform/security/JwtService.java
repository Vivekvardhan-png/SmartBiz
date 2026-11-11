package com.nexusops.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies short-lived HMAC-SHA256 JWT access tokens.
 */
@Service
public class JwtService {

    public static final String CLAIM_ORGANIZATION_ID = "org";

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.getExpirationSeconds();
    }

    public String issueAccessToken(UUID userId, UUID sessionId, UUID organizationId, Instant issuedAt) {
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .claim(CLAIM_ORGANIZATION_ID, organizationId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public ParsedToken parseAndVerify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.getId());
            String orgClaim = claims.get(CLAIM_ORGANIZATION_ID, String.class);
            if (orgClaim == null) {
                throw new InvalidTokenException("Missing organization claim.");
            }
            UUID organizationId = UUID.fromString(orgClaim);
            Instant expiresAt = claims.getExpiration().toInstant();

            return new ParsedToken(userId, sessionId, organizationId, expiresAt);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired authentication token.");
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public record ParsedToken(
            UUID userId,
            UUID sessionId,
            UUID organizationId,
            Instant expiresAt
    ) {}

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}

