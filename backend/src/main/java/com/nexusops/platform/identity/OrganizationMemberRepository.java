package com.nexusops.platform.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    @Query("""
            SELECT m FROM OrganizationMember m
            JOIN FETCH m.organization
            JOIN FETCH m.user
            WHERE m.organization.id = :organizationId AND m.user.id = :userId
            """)
    Optional<OrganizationMember> findWithDetailsByOrganizationIdAndUserId(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT m FROM OrganizationMember m
            JOIN FETCH m.organization
            WHERE m.user.id = :userId
            ORDER BY m.joinedAt ASC
            """)
    List<OrganizationMember> findAllWithOrganizationByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT m FROM OrganizationMember m
            JOIN FETCH m.user
            WHERE m.organization.id = :organizationId
            ORDER BY m.joinedAt ASC
            """)
    List<OrganizationMember> findAllWithUserByOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT m FROM OrganizationMember m
            JOIN FETCH m.user
            WHERE m.id = :memberId AND m.organization.id = :organizationId
            """)
    Optional<OrganizationMember> findByIdAndOrganizationId(
            @Param("memberId") UUID memberId,
            @Param("organizationId") UUID organizationId
    );

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    long countByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);
}

