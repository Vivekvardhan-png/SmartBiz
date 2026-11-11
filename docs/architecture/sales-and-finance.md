# Sales Orders, Invoices & Payments Architecture

**Phase:** Prompt 05  
**Status:** Active Specification

## 1. Monetary Arithmetic & Tax Disclaimer

- All monetary amounts use Java `BigDecimal` (`NUMERIC(14, 2)`, `RoundingMode.HALF_UP`) and quantities use `NUMERIC(14, 3)`. Floating-point arithmetic is never used.
- Taxes are modeled strictly as a simple configurable flat percentage (`tax_rate_percent` in `[0.00, 100.00]`) applied to `(subtotal_amount - discount_amount)`. **This platform does not claim jurisdiction-specific statutory tax compliance or automated tax filing.**

## 2. Sales Orders & Inventory Integration

- **Statuses:** `DRAFT`, `CONFIRMED`, `FULFILLED`, `CANCELLED`
- **Transitions:**
  - `DRAFT → CONFIRMED`: Acquires `PESSIMISTIC_WRITE` lock on the order, validates all active products, snapshots unit prices, calculates line/order totals on the backend, and deducts stock atomically via `StockMovementService` (`ORDER_DEDUCTION`, idempotency key `order-confirm:{orderId}:{itemId}`). If any item lacks stock, the entire transaction rolls back.
  - `DRAFT → CANCELLED`: Cancels without touching inventory.
  - `CONFIRMED → FULFILLED`: Marks fulfillment complete (stock was already deducted at `CONFIRMED`).
  - `CONFIRMED → CANCELLED`: Allowed only if no invoice exists for the order; atomically restores deducted inventory via `StockMovementService` (`RETURN_IN`, idempotency key `order-cancel:{orderId}:{itemId}`).
- Every creation and transition appends an audit row to `sales_order_history`.

## 3. Invoices & Retry Safety

- **Statuses:** `ISSUED`, `PARTIALLY_PAID`, `PAID`, `REFUNDED`, `VOID`
- **Sequential Numbering:** Per-organization monotonic counter in `invoice_sequences` (`INV-000001`, `INV-000002`, ...) protected by `PESSIMISTIC_WRITE` lock and `UNIQUE (organization_id, invoice_number)`.
- **Idempotency:** `invoices` enforces `UNIQUE (organization_id, sales_order_id)` and acquires a lock on the `SalesOrder` before generating. Retries return the existing invoice (`alreadyExisted = true`) without generating duplicates.

## 4. Payments & Refunds (Append-Only Ledger)

- Payments (`PAYMENT`) and refunds (`REFUND`) are immutable rows in `payments`. Existing payment rows are never overwritten or deleted.
- Under a `PESSIMISTIC_WRITE` lock on the `Invoice`:
  - `PAYMENT` requires `amount > 0` and `paid_amount + amount <= total_amount` (overpayments rejected with `422`).
  - `REFUND` requires `amount > 0` and `amount <= paid_amount` (over-refunds rejected with `422`).
  - `paid_amount` and `status` are reconciled server-side from recorded ledger entries.

## 5. Dashboard Reconciliation

- `salesOrdersByStatus`: Orders in `organization_id` grouped by status within `[periodStart, periodEnd]` UTC.
- `periodRevenue`: `SUM(invoices.total_amount)` for non-voided, non-refunded invoices (`ISSUED`, `PARTIALLY_PAID`, `PAID`) with `issue_date` in `[periodStart, periodEnd]` (avoids double-counting orders + invoices + payments).
- `outstandingInvoiceBalance`: `SUM(invoices.total_amount - invoices.paid_amount)` for `ISSUED` and `PARTIALLY_PAID` invoices.

