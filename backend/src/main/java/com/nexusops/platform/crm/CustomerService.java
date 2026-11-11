package com.nexusops.platform.crm;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCrmNoteRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCustomerRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CrmNoteResponse;
import com.nexusops.platform.crm.dto.CrmDtos.CustomerResponse;
import com.nexusops.platform.crm.dto.CrmDtos.UpdateCustomerRequest;
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
public class CustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final CrmNoteRepository noteRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            CrmNoteRepository noteRepository) {
        this.customerRepository = customerRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> listCustomers(
            TenantPrincipal principal,
            String search,
            CustomerStatus status,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<Customer> spec = buildSpec(principal.organizationId(), search, status);
        Page<Customer> result = customerRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PagedResponse.of(
                result.getContent().stream().map(CustomerService::toResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(TenantPrincipal principal, UUID customerId) {
        Customer customer = customerRepository
                .findByIdAndOrganizationId(customerId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(TenantPrincipal principal, CreateCustomerRequest request) {
        String normalizedEmail = normalizeOptionalEmail(request.email());
        if (normalizedEmail != null && customerRepository.existsByOrganizationIdAndEmailIgnoreCase(
                principal.organizationId(), normalizedEmail)) {
            throw new ConflictException(
                    "A customer with email '" + normalizedEmail + "' already exists in this organization.");
        }

        Customer customer = new Customer(
                principal.organizationId(),
                request.name().trim(),
                principal.userId());
        customer.setCompanyName(trimToNull(request.companyName()));
        customer.setEmail(normalizedEmail);
        customer.setPhone(trimToNull(request.phone()));
        customer.setAddress(trimToNull(request.address()));
        customer.setFollowUpDate(request.followUpDate());
        customer.setNotes(trimToNull(request.notes()));

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse updateCustomer(
            TenantPrincipal principal,
            UUID customerId,
            UpdateCustomerRequest request) {

        Customer customer = customerRepository
                .findByIdAndOrganizationId(customerId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Customer name cannot be blank.");
            }
            customer.setName(trimmed);
        }
        if (request.companyName() != null) {
            customer.setCompanyName(trimToNull(request.companyName()));
        }
        if (request.email() != null) {
            String normalizedEmail = normalizeOptionalEmail(request.email());
            if (normalizedEmail != null
                    && customerRepository.existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(
                            principal.organizationId(), normalizedEmail, customer.getId())) {
                throw new ConflictException(
                        "A customer with email '" + normalizedEmail + "' already exists in this organization.");
            }
            customer.setEmail(normalizedEmail);
        }
        if (request.phone() != null) {
            customer.setPhone(trimToNull(request.phone()));
        }
        if (request.address() != null) {
            customer.setAddress(trimToNull(request.address()));
        }
        if (request.followUpDate() != null) {
            customer.setFollowUpDate(request.followUpDate());
        }
        if (request.notes() != null) {
            customer.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null) {
            customer.setStatus(request.status());
        }

        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<CrmNoteResponse> listNotes(TenantPrincipal principal, UUID customerId) {
        customerRepository.findByIdAndOrganizationId(customerId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        return noteRepository.findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        principal.organizationId(), CrmNoteEntityType.CUSTOMER, customerId)
                .stream()
                .map(CustomerService::toNoteResponse)
                .toList();
    }

    @Transactional
    public CrmNoteResponse addNote(
            TenantPrincipal principal,
            UUID customerId,
            CreateCrmNoteRequest request) {

        Customer customer = customerRepository
                .findByIdAndOrganizationId(customerId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        if (request.followUpDate() != null) {
            customer.setFollowUpDate(request.followUpDate());
        }

        String authorName = principal.displayName() != null && !principal.displayName().isBlank()
                ? principal.displayName()
                : principal.email();

        CrmNote note = noteRepository.save(new CrmNote(
                principal.organizationId(),
                CrmNoteEntityType.CUSTOMER,
                customer.getId(),
                request.content().trim(),
                request.followUpDate(),
                principal.userId(),
                authorName
        ));

        return toNoteResponse(note);
    }

    private Specification<Customer> buildSpec(UUID organizationId, String search, CustomerStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeOptionalEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getCompanyName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getFollowUpDate(),
                customer.getNotes(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    static CrmNoteResponse toNoteResponse(CrmNote note) {
        return new CrmNoteResponse(
                note.getId(),
                note.getEntityType(),
                note.getEntityId(),
                note.getContent(),
                note.getFollowUpDate(),
                note.getAuthorName(),
                note.getCreatedAt()
        );
    }
}

