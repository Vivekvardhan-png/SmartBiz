package com.nexusops.platform.inventory;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.CreateProductRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.LinkSupplierProductRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.ProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.RecordStockMovementRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.StockMovementResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.UpdateProductRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Products, supplier links, stock movements, and movement audit history.")
public class ProductController {

    private final ProductService productService;
    private final StockMovementService stockMovementService;
    private final TenantContext tenantContext;

    public ProductController(
            ProductService productService,
            StockMovementService stockMovementService,
            TenantContext tenantContext) {
        this.productService = productService;
        this.stockMovementService = stockMovementService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "List, search, filter, and paginate products")
    public ResponseEntity<PagedResponse<ProductResponse>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(productService.listProducts(
                tenantContext.requirePrincipal(),
                search,
                category,
                lowStockOnly,
                active,
                page,
                pageSize));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "Get product details")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.of(
                productService.getProduct(tenantContext.requirePrincipal(), productId)));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Create a product (with optional initial stock movement)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        productService.createProduct(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Update product metadata (stock quantity must change via movements)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                productService.updateProduct(tenantContext.requirePrincipal(), productId, request)));
    }

    @GetMapping("/products/{productId}/suppliers")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "List suppliers linked to a product")
    public ResponseEntity<ApiResponse<List<SupplierProductResponse>>> listProductSuppliers(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.of(
                productService.listSuppliersForProduct(tenantContext.requirePrincipal(), productId)));
    }

    @PostMapping("/products/{productId}/suppliers")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Link or update a supplier for a product")
    public ResponseEntity<ApiResponse<SupplierProductResponse>> linkSupplier(
            @PathVariable UUID productId,
            @Valid @RequestBody LinkSupplierProductRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                productService.linkSupplier(tenantContext.requirePrincipal(), productId, request)));
    }

    @DeleteMapping("/products/{productId}/suppliers/{supplierId}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Remove a supplier link from a product")
    public ResponseEntity<Void> unlinkSupplier(
            @PathVariable UUID productId,
            @PathVariable UUID supplierId) {
        productService.unlinkSupplier(tenantContext.requirePrincipal(), productId, supplierId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/{productId}/movements")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    @Operation(summary = "Record a stock receipt, adjustment, or return movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> recordMovement(
            @PathVariable UUID productId,
            @Valid @RequestBody RecordStockMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        stockMovementService.recordManualMovement(
                                tenantContext.requirePrincipal(), productId, request)));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    @Operation(summary = "List stock movement history for the active organization")
    public ResponseEntity<PagedResponse<StockMovementResponse>> listMovements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize) {
        return ResponseEntity.ok(stockMovementService.listMovements(
                tenantContext.requirePrincipal(), productId, page, pageSize));
    }
}

