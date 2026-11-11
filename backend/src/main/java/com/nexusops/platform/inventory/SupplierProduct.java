package com.nexusops.platform.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "supplier_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_supplier_products_pair",
                columnNames = {"organization_id", "supplier_id", "product_id"}
        )
)
public class SupplierProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupplierProduct() {
    }

    public SupplierProduct(UUID organizationId, Supplier supplier, Product product) {
        this.organizationId = organizationId;
        this.supplier = supplier;
        this.product = product;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Product getProduct() {
        return product;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public void setSupplierSku(String supplierSku) {
        this.supplierSku = supplierSku;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(Integer leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

