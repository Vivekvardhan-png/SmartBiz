# Inventory & Supplier Management Architecture

**Phase:** Prompt 04  
**Status:** Active Specification

## 1. Stock Integrity & Movement Model

- **Source of Truth Invariant:** Every product's `stock_quantity` (`NUMERIC(14, 3)`) is derived exclusively from auditable `stock_movements` rows:
  $$\text{products.stock\_quantity} = \sum \text{stock\_movements.quantity\_delta}$$
- **No Arbitrary Edits:** `PATCH /api/v1/inventory/products/{id}` updates product metadata (`name`, `sku`, `category`, `unitOfMeasure`, `unitPrice`, `costPrice`, `lowStockThreshold`, `active`, `supplierIds`) and **never** permits arbitrary overwrite of `stock_quantity`.
- **Non-Negative Stock Rule:** `stock_quantity >= 0` is enforced at both the service layer and PostgreSQL `CHECK` constraint. Any movement or deduction that would make `stock_quantity < 0` is rejected (`422 Unprocessable Entity`) and the transaction rolls back.
- **Supported Movement Types (`StockMovementType`):**
  - `INITIAL` (`+`) — initial opening balance recorded atomically when a product is created with `initialStock > 0`
  - `RECEIPT` (`+`) — inbound stock receipt (optionally linked to a supplier)
  - `ADJUSTMENT_IN` (`+`) — positive cycle-count correction
  - `ADJUSTMENT_OUT` (`-`) — negative adjustment (damage, shrinkage, manual write-off)
  - `RETURN_IN` (`+`) — restock from customer return or cancelled order
  - `ORDER_DEDUCTION` (`-`) — reserved for Prompt 05 sales order stock deductions

## 2. Concurrency & Retry Safety

1. `StockMovementService.recordMovement` executes inside `@Transactional` and acquires a `PESSIMISTIC_WRITE` row lock (`SELECT ... FOR UPDATE`) on the `Product` row scoped by `organization_id`.
2. Concurrent adjustments or orders serialize on the locked product row so simultaneous requests cannot oversell or violate `stock_quantity >= 0`.
3. Optional `idempotencyKey` (unique per `organization_id` via partial unique index `uq_stock_movements_org_idempotency`) prevents network retries from applying the same movement twice; replays return the recorded movement with `idempotentReplay = true`.

## 3. Low-Stock Dashboard Integration

- A product is considered low-stock when `active = true AND stock_quantity <= low_stock_threshold`.
- `GET /api/v1/dashboard/summary` exposes live `lowStockProductsCount` (`moduleAvailable = true`) using an organization-scoped query on `products`.

