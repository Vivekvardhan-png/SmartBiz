package com.nexusops.platform.crm;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.crm.dto.CrmDtos.ConvertLeadRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCrmNoteRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateLeadRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CrmNoteResponse;
import com.nexusops.platform.crm.dto.CrmDtos.LeadConversionResponse;
import com.nexusops.platform.crm.dto.CrmDtos.LeadResponse;
import com.nexusops.platform.crm.dto.CrmDtos.UpdateLeadRequest;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LeadService {

    private static final int MAX_PAGE_SIZE = 100;

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final CrmNoteRepository noteRepository;

    public LeadService(
            LeadRepository leadRepository,
            CustomerRepository customerRepository,
            CrmNoteRepository noteRepository) {
        this.leadRepository = leadRepository;
        this.customerRepository = customerRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeadResponse> listLeads(
            TenantPrincipal principal,
            String search,
            LeadStatus status,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<Lead> spec = buildSpec(principal.organizationId(), search, status);
        Page<Lead> result = leadRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PagedResponse.of(
                result.getContent().stream().map(LeadService::toResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public LeadResponse getLead(TenantPrincipal principal, UUID leadId) {
        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse createLead(TenantPrincipal principal, CreateLeadRequest request) {
        Lead lead = new Lead(
                principal.organizationId(),
                request.title().trim(),
                request.contactName().trim(),
                principal.userId());
        lead.setCompanyName(CustomerService.trimToNull(request.companyName()));
        lead.setEmail(CustomerService.normalizeOptionalEmail(request.email()));
        lead.setPhone(CustomerService.trimToNull(request.phone()));
        lead.setSource(CustomerService.trimToNull(request.source()));
        lead.setEstimatedValue(request.estimatedValue());
        lead.setFollowUpDate(request.followUpDate());
        lead.setNotes(CustomerService.trimToNull(request.notes()));

        return toResponse(leadRepository.save(lead));
    }

    @Transactional
    public LeadResponse updateLead(
            TenantPrincipal principal,
            UUID leadId,
            UpdateLeadRequest request) {

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (request.status() != null && request.status() != lead.getStatus()) {
            if (request.status() == LeadStatus.CONVERTED) {
                throw new BusinessRuleException(
                        "Leads must be converted using the dedicated conversion endpoint.");
            }
            if (!lead.getStatus().canManuallyTransitionTo(request.status())) {
                throw new BusinessRuleException(
                        "Cannot transition lead from " + lead.getStatus() + " to " + request.status() + ".");
            }
            lead.setStatus(request.status());
        }

        if (request.title() != null) {
            String trimmed = request.title().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Lead title cannot be blank.");
            }
            lead.setTitle(trimmed);
        }
        if (request.contactName() != null) {
            String trimmed = request.contactName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Contact name cannot be blank.");
            }
            lead.setContactName(trimmed);
        }
        if (request.companyName() != null) {
            lead.setCompanyName(CustomerService.trimToNull(request.companyName()));
        }
        if (request.email() != null) {
            lead.setEmail(CustomerService.normalizeOptionalEmail(request.email()));
        }
        if (request.phone() != null) {
            lead.setPhone(CustomerService.trimToNull(request.phone()));
        }
        if (request.source() != null) {
            lead.setSource(CustomerService.trimToNull(request.source()));
        }
        if (request.estimatedValue() != null) {
            lead.setEstimatedValue(request.estimatedValue());
        }
        if (request.followUpDate() != null) {
            lead.setFollowUpDate(request.followUpDate());
        }
        if (request.notes() != null) {
            lead.setNotes(CustomerService.trimToNull(request.notes()));
        }

        return toResponse(lead);
    }

    @Transactional
    public LeadConversionResponse convertLead(
            TenantPrincipal principal,
            UUID leadId,
            ConvertLeadRequest request) {

        Lead lead = leadRepository
                .findForUpdateByIdAndOrganizationId(leadId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (lead.getStatus() == LeadStatus.CONVERTED && lead.getConvertedCustomerId() != null) {
            Customer existing = customerRepository
                    .findByIdAndOrganizationId(lead.getConvertedCustomerId(), principal.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer", lead.getConvertedCustomerId()));
            return new LeadConversionResponse(
                    toResponse(lead),
                    CustomerService.toResponse(existing),
                    true
            );
        }

        if (lead.getStatus() == LeadStatus.LOST) {
            throw new BusinessRuleException(
                    "Lost leads cannot be converted. Re-open the lead before converting.");
        }

        boolean linkExisting = request != null && Boolean.TRUE.equals(request.linkExistingCustomer());
        Customer targetCustomer = null;

        if (lead.getEmail() != null) {
            Customer existingByEmail = customerRepository
                    .findByOrganizationIdAndEmailIgnoreCase(principal.organizationId(), lead.getEmail())
                    .orElse(null);

            if (existingByEmail != null) {
                if (!linkExisting) {
                    throw new ConflictException(
                            "A customer with email '" + lead.getEmail()
                                    + "' already exists in this organization.");
                }
                targetCustomer = existingByEmail;
            }
        }

        if (targetCustomer == null) {
            Customer customer = new Customer(
                    principal.organizationId(),
                    lead.getContactName(),
                    principal.userId());
            customer.setCompanyName(lead.getCompanyName());
            customer.setEmail(lead.getEmail());
            customer.setPhone(lead.getPhone());
            customer.setFollowUpDate(lead.getFollowUpDate());
            customer.setNotes(lead.getNotes());
            targetCustomer = customerRepository.save(customer);
        }

        lead.markConverted(targetCustomer.getId(), Instant.now());

        return new LeadConversionResponse(
                toResponse(lead),
                CustomerService.toResponse(targetCustomer),
                false
        );
    }

    @Transactional(readOnly = true)
    public List<CrmNoteResponse> listNotes(TenantPrincipal principal, UUID leadId) {
        leadRepository.findByIdAndOrganizationId(leadId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        return noteRepository.findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        principal.organizationId(), CrmNoteEntityType.LEAD, leadId)
                .stream()
                .map(CustomerService::toNoteResponse)
                .toList();
    }

    @Transactional
    public CrmNoteResponse addNote(
            TenantPrincipal principal,
            UUID leadId,
            CreateCrmNoteRequest request) {

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (request.followUpDate() != null) {
            lead.setFollowUpDate(request.followUpDate());
        }

        String authorName = principal.displayName() != null && !principal.displayName().isBlank()
                ? principal.displayName()
                : principal.email();

        CrmNote note = noteRepository.save(new CrmNote(
                principal.organizationId(),
                CrmNoteEntityType.LEAD,
                lead.getId(),
                request.content().trim(),
                request.followUpDate(),
                principal.userId(),
                authorName
        ));

        return CustomerService.toNoteResponse(note);
    }

    private Specification<Lead> buildSpec(UUID organizationId, String search, LeadStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("contactName")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static LeadResponse toResponse(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getTitle(),
                lead.getContactName(),
                lead.getCompanyName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getStatus(),
                lead.getStatus().allowedManualTransitions(),
                lead.getEstimatedValue(),
                lead.getFollowUpDate(),
                lead.getNotes(),
                lead.getConvertedCustomerId(),
                lead.getConvertedAt(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }
}

