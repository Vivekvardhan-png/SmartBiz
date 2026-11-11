package com.nexusops.platform.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Customer> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(
            UUID organizationId, String email, UUID id);

    long countByOrganizationId(UUID organizationId);
}

