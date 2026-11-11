package com.nexusops.platform.inventory;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.CreateSupplierRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.UpdateSupplierRequest;
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
@RequestMapping("/api/v1/inventory/suppliers")
@Tag(name = "Suppliers", description = "Organization-scoped supplier directory and linked products.")
public class SupplierController {

    private final SupplierService supplierService;
    private final TenantContext tenantContext;

    public SupplierController(SupplierService supplierService, TenantContext tenantContext) {
        this.supplierService = supplierService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "List, search, filter, and paginate suppliers")
    public ResponseEntity<PagedResponse<SupplierResponse>> listSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(supplierService.listSuppliers(
                tenantContext.requirePrincipal(), search, active, page, pageSize));
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "Get supplier details")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplier(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.of(
                supplierService.getSupplier(tenantContext.requirePrincipal(), supplierId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Create a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        supplierService.createSupplier(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Update a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                supplierService.updateSupplier(tenantContext.requirePrincipal(), supplierId, request)));
    }

    @GetMapping("/{supplierId}/products")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "List products linked to a supplier")
    public ResponseEntity<ApiResponse<List<SupplierProductResponse>>> listSupplierProducts(
            @PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.of(
                supplierService.listSupplierProducts(tenantContext.requirePrincipal(), supplierId)));
    }
}

