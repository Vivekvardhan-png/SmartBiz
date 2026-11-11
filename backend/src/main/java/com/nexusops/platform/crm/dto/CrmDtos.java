package com.nexusops.platform.crm.dto;

import com.nexusops.platform.crm.CrmNoteEntityType;
import com.nexusops.platform.crm.CustomerStatus;
import com.nexusops.platform.crm.LeadStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class CrmDtos {

    private CrmDtos() {
    }

    public record CreateCustomerRequest(
            @NotBlank(message = "Customer name is required.")
            @Size(max = 255)
            String name,

            @Size(max = 255)
            String companyName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 2000)
            String address,

            LocalDate followUpDate,

            @Size(max = 4000)
            String notes
    ) {}

    public record UpdateCustomerRequest(
            @Size(max = 255)
            String name,

            @Size(max = 255)
            String companyName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 2000)
            String address,

            LocalDate followUpDate,

            @Size(max = 4000)
            String notes,

            CustomerStatus status
    ) {}

    public record CustomerResponse(
            UUID id,
            String name,
            String companyName,
            String email,
            String phone,
            String address,
            LocalDate followUpDate,
            String notes,
            CustomerStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateLeadRequest(
            @NotBlank(message = "Lead title is required.")
            @Size(max = 255)
            String title,

            @NotBlank(message = "Contact name is required.")
            @Size(max = 255)
            String contactName,

            @Size(max = 255)
            String companyName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 100)
            String source,

            @DecimalMin(value = "0.00", message = "Estimated value must be zero or positive.")
            BigDecimal estimatedValue,

            LocalDate followUpDate,

            @Size(max = 4000)
            String notes
    ) {}

    public record UpdateLeadRequest(
            @Size(max = 255)
            String title,

            @Size(max = 255)
            String contactName,

            @Size(max = 255)
            String companyName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 100)
            String source,

            LeadStatus status,

            @DecimalMin(value = "0.00", message = "Estimated value must be zero or positive.")
            BigDecimal estimatedValue,

            LocalDate followUpDate,

            @Size(max = 4000)
            String notes
    ) {}

    public record ConvertLeadRequest(
            Boolean linkExistingCustomer
    ) {}

    public record LeadResponse(
            UUID id,
            String title,
            String contactName,
            String companyName,
            String email,
            String phone,
            String source,
            LeadStatus status,
            Set<LeadStatus> allowedNextStatuses,
            BigDecimal estimatedValue,
            LocalDate followUpDate,
            String notes,
            UUID convertedCustomerId,
            Instant convertedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record LeadConversionResponse(
            LeadResponse lead,
            CustomerResponse customer,
            boolean alreadyConverted
    ) {}

    public record CreateCrmNoteRequest(
            @NotBlank(message = "Note content is required.")
            @Size(max = 4000)
            String content,

            LocalDate followUpDate
    ) {}

    public record CrmNoteResponse(
            UUID id,
            CrmNoteEntityType entityType,
            UUID entityId,
            String content,
            LocalDate followUpDate,
            String authorName,
            Instant createdAt
    ) {}
}

