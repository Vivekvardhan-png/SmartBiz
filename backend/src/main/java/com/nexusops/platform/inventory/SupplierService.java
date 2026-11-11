package com.nexusops.platform.inventory;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.inventory.dto.InventoryDtos.CreateSupplierRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.UpdateSupplierRequest;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SupplierService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierProductRepository supplierProductRepository) {
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SupplierResponse> listSuppliers(
            TenantPrincipal principal,
            String search,
            Boolean active,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<Supplier> spec = buildSpec(principal.organizationId(), search, active);
        Page<Supplier> result = supplierRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "name"))
        );

        return PagedResponse.of(
                result.getContent().stream().map(SupplierService::toResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(TenantPrincipal principal, UUID supplierId) {
        Supplier supplier = supplierRepository
                .findByIdAndOrganizationId(supplierId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse createSupplier(TenantPrincipal principal, CreateSupplierRequest request) {
        String normalizedEmail = normalizeOptionalEmail(request.email());
        if (normalizedEmail != null && supplierRepository.existsByOrganizationIdAndEmailIgnoreCase(
                principal.organizationId(), normalizedEmail)) {
            throw new ConflictException(
                    "A supplier with email '" + normalizedEmail + "' already exists in this organization.");
        }

        Supplier supplier = new Supplier(
                principal.organizationId(),
                request.name().trim(),
                principal.userId());
        supplier.setContactName(trimToNull(request.contactName()));
        supplier.setEmail(normalizedEmail);
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setNotes(trimToNull(request.notes()));

        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(
            TenantPrincipal principal,
            UUID supplierId,
            UpdateSupplierRequest request) {

        Supplier supplier = supplierRepository
                .findByIdAndOrganizationId(supplierId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Supplier name cannot be blank.");
            }
            supplier.setName(trimmed);
        }
        if (request.contactName() != null) {
            supplier.setContactName(trimToNull(request.contactName()));
        }
        if (request.email() != null) {
            String normalizedEmail = normalizeOptionalEmail(request.email());
            if (normalizedEmail != null
                    && supplierRepository.existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(
                            principal.organizationId(), normalizedEmail, supplier.getId())) {
                throw new ConflictException(
                        "A supplier with email '" + normalizedEmail + "' already exists in this organization.");
            }
            supplier.setEmail(normalizedEmail);
        }
        if (request.phone() != null) {
            supplier.setPhone(trimToNull(request.phone()));
        }
        if (request.address() != null) {
            supplier.setAddress(trimToNull(request.address()));
        }
        if (request.notes() != null) {
            supplier.setNotes(trimToNull(request.notes()));
        }
        if (request.active() != null) {
            supplier.setActive(request.active());
        }

        return toResponse(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> listSupplierProducts(
            TenantPrincipal principal,
            UUID supplierId) {
        supplierRepository.findByIdAndOrganizationId(supplierId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));

        return supplierProductRepository
                .findAllWithDetailsByOrganizationIdAndSupplierId(principal.organizationId(), supplierId)
                .stream()
                .map(ProductService::toSupplierProductResponse)
                .toList();
    }

    private Specification<Supplier> buildSpec(UUID organizationId, String search, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeOptionalEmail(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getNotes(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}

