package com.nexusops.platform.inventory;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.inventory.dto.InventoryDtos.CreateProductRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.LinkSupplierProductRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.ProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.SupplierProductResponse;
import com.nexusops.platform.inventory.dto.InventoryDtos.UpdateProductRequest;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final StockMovementService stockMovementService;

    public ProductService(
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            SupplierProductRepository supplierProductRepository,
            StockMovementService stockMovementService) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.stockMovementService = stockMovementService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> listProducts(
            TenantPrincipal principal,
            String search,
            String category,
            Boolean lowStockOnly,
            Boolean active,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<Product> spec = buildSpec(
                principal.organizationId(), search, category, lowStockOnly, active);

        Page<Product> result = productRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "name"))
        );

        return PagedResponse.of(
                result.getContent().stream().map(ProductService::toResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(TenantPrincipal principal, UUID productId) {
        Product product = productRepository
                .findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(TenantPrincipal principal, CreateProductRequest request) {
        String normalizedSku = normalizeSku(request.sku());
        if (productRepository.existsByOrganizationIdAndSkuIgnoreCase(
                principal.organizationId(), normalizedSku)) {
            throw new ConflictException(
                    "A product with SKU '" + normalizedSku + "' already exists in this organization.");
        }

        Product product = new Product(
                principal.organizationId(),
                normalizedSku,
                request.name().trim(),
                request.unitOfMeasure().trim().toUpperCase(Locale.ROOT),
                request.unitPrice().setScale(2, RoundingMode.HALF_UP),
                request.lowStockThreshold().setScale(3, RoundingMode.HALF_UP),
                principal.userId());
        product.setDescription(SupplierService.trimToNull(request.description()));
        product.setCategory(SupplierService.trimToNull(request.category()));
        if (request.costPrice() != null) {
            product.setCostPrice(request.costPrice().setScale(2, RoundingMode.HALF_UP));
        }

        Product saved = productRepository.save(product);

        if (request.initialStock() != null && request.initialStock().compareTo(BigDecimal.ZERO) > 0) {
            stockMovementService.applyMovement(
                    principal.organizationId(),
                    saved.getId(),
                    null,
                    StockMovementType.INITIAL,
                    request.initialStock(),
                    "Initial opening stock balance",
                    null,
                    null,
                    principal.userId(),
                    StockMovementService.resolveActorName(principal)
            );
            saved = productRepository
                    .findByIdAndOrganizationId(saved.getId(), principal.organizationId())
                    .orElse(saved);
        }

        return toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(
            TenantPrincipal principal,
            UUID productId,
            UpdateProductRequest request) {

        Product product = productRepository
                .findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (request.sku() != null) {
            String normalizedSku = normalizeSku(request.sku());
            if (normalizedSku.isEmpty()) {
                throw new BusinessRuleException("SKU cannot be blank.");
            }
            if (productRepository.existsByOrganizationIdAndSkuIgnoreCaseAndIdNot(
                    principal.organizationId(), normalizedSku, product.getId())) {
                throw new ConflictException(
                        "A product with SKU '" + normalizedSku + "' already exists in this organization.");
            }
            product.setSku(normalizedSku);
        }
        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Product name cannot be blank.");
            }
            product.setName(trimmed);
        }
        if (request.description() != null) {
            product.setDescription(SupplierService.trimToNull(request.description()));
        }
        if (request.category() != null) {
            product.setCategory(SupplierService.trimToNull(request.category()));
        }
        if (request.unitOfMeasure() != null) {
            String trimmed = request.unitOfMeasure().trim().toUpperCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Unit of measure cannot be blank.");
            }
            product.setUnitOfMeasure(trimmed);
        }
        if (request.unitPrice() != null) {
            product.setUnitPrice(request.unitPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.costPrice() != null) {
            product.setCostPrice(request.costPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.lowStockThreshold() != null) {
            product.setLowStockThreshold(request.lowStockThreshold().setScale(3, RoundingMode.HALF_UP));
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> listSuppliersForProduct(
            TenantPrincipal principal,
            UUID productId) {
        productRepository.findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        return supplierProductRepository
                .findAllWithDetailsByOrganizationIdAndProductId(principal.organizationId(), productId)
                .stream()
                .map(ProductService::toSupplierProductResponse)
                .toList();
    }

    @Transactional
    public SupplierProductResponse linkSupplier(
            TenantPrincipal principal,
            UUID productId,
            LinkSupplierProductRequest request) {

        Product product = productRepository
                .findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        Supplier supplier = supplierRepository
                .findByIdAndOrganizationId(request.supplierId(), principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        SupplierProduct link = supplierProductRepository
                .findByOrganizationIdAndSupplierIdAndProductId(
                        principal.organizationId(), supplier.getId(), product.getId())
                .orElseGet(() -> new SupplierProduct(principal.organizationId(), supplier, product));

        link.setSupplierSku(SupplierService.trimToNull(request.supplierSku()));
        link.setUnitCost(request.unitCost() != null
                ? request.unitCost().setScale(2, RoundingMode.HALF_UP)
                : null);
        link.setLeadTimeDays(request.leadTimeDays());

        return toSupplierProductResponse(supplierProductRepository.save(link));
    }

    @Transactional
    public void unlinkSupplier(TenantPrincipal principal, UUID productId, UUID supplierId) {
        productRepository.findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        supplierRepository.findByIdAndOrganizationId(supplierId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));

        supplierProductRepository.deleteByOrganizationIdAndSupplierIdAndProductId(
                principal.organizationId(), supplierId, productId);
    }

    private Specification<Product> buildSpec(
            UUID organizationId,
            String search,
            String category,
            Boolean lowStockOnly,
            Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(cb.coalesce(root.get("category"), "")),
                        category.trim().toLowerCase(Locale.ROOT)));
            }
            if (Boolean.TRUE.equals(lowStockOnly)) {
                predicates.add(cb.isTrue(root.get("active")));
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("stockQuantity"),
                        root.get("lowStockThreshold")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeSku(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getUnitOfMeasure(),
                product.getUnitPrice(),
                product.getCostPrice(),
                product.getStockQuantity(),
                product.getLowStockThreshold(),
                product.isLowStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public static SupplierProductResponse toSupplierProductResponse(SupplierProduct sp) {
        return new SupplierProductResponse(
                sp.getId(),
                sp.getSupplier().getId(),
                sp.getSupplier().getName(),
                sp.getProduct().getId(),
                sp.getProduct().getSku(),
                sp.getProduct().getName(),
                sp.getSupplierSku(),
                sp.getUnitCost(),
                sp.getLeadTimeDays(),
                sp.getCreatedAt()
        );
    }
}

