package com.nexusops.platform.crm;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCrmNoteRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCustomerRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CrmNoteResponse;
import com.nexusops.platform.crm.dto.CrmDtos.CustomerResponse;
import com.nexusops.platform.crm.dto.CrmDtos.UpdateCustomerRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/customers")
@Tag(name = "CRM Customers", description = "Organization-scoped customer records and follow-up notes.")
public class CustomerController {

    private final CustomerService customerService;
    private final TenantContext tenantContext;

    public CustomerController(CustomerService customerService, TenantContext tenantContext) {
        this.customerService = customerService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "List, search, filter, and paginate customers")
    public ResponseEntity<PagedResponse<CustomerResponse>> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(customerService.listCustomers(
                tenantContext.requirePrincipal(), search, status, page, pageSize));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "Get a customer record")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.of(
                customerService.getCustomer(tenantContext.requirePrincipal(), customerId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Create a customer record")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        customerService.createCustomer(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/{customerId}")
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Update a customer record")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                customerService.updateCustomer(tenantContext.requirePrincipal(), customerId, request)));
    }

    @GetMapping("/{customerId}/notes")
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "List follow-up notes for a customer")
    public ResponseEntity<ApiResponse<List<CrmNoteResponse>>> listNotes(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.of(
                customerService.listNotes(tenantContext.requirePrincipal(), customerId)));
    }

    @PostMapping("/{customerId}/notes")
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Add a follow-up note to a customer")
    public ResponseEntity<ApiResponse<CrmNoteResponse>> addNote(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateCrmNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        customerService.addNote(tenantContext.requirePrincipal(), customerId, request)));
    }
}

