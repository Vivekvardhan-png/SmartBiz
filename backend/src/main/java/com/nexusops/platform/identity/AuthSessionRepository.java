package com.nexusops.platform.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByIdAndRevokedAtIsNull(UUID id);

    @Modifying
    @Query("""
            UPDATE AuthSession s
            SET s.revokedAt = :revokedAt
            WHERE s.userId = :userId
              AND s.organizationId = :organizationId
              AND s.revokedAt IS NULL
            """)
    int revokeActiveSessionsForMembership(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("revokedAt") Instant revokedAt
    );
}

