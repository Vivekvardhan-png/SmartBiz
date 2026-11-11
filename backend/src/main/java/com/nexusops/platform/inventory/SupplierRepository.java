package com.nexusops.platform.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository
        extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(
            UUID organizationId, String email, UUID id);
}

