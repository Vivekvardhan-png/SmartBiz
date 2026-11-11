# Dashboard & CRM Architecture Specification

**Phase:** Prompt 03  
**Status:** Active Specification

## 1. CRM Domain Model & Rules

### Lead Pipeline Statuses
Allowed values: `NEW`, `CONTACTED`, `QUALIFIED`, `PROPOSAL`, `CONVERTED`, `LOST`.

- **Open stages:** `NEW`, `CONTACTED`, `QUALIFIED`, `PROPOSAL`
- **Terminal stages:** `CONVERTED`, `LOST`

#### Allowed Manual Transitions (`PATCH /api/v1/crm/leads/{id}`)
- `NEW` → `CONTACTED`, `QUALIFIED`, `PROPOSAL`, `LOST`
- `CONTACTED` → `QUALIFIED`, `PROPOSAL`, `LOST`
- `QUALIFIED` → `PROPOSAL`, `LOST`
- `PROPOSAL` → `QUALIFIED`, `LOST`
- `LOST` → `CONTACTED`, `QUALIFIED` (re-opening a lost lead)
- `CONVERTED` → terminal (cannot manually revert to an open or lost status)
- Transitioning into `CONVERTED` via `PATCH` is rejected (`422`); conversion must execute through `POST /api/v1/crm/leads/{id}/convert`.

### Lead-to-Customer Conversion & Retry Safety
1. Conversion runs inside a single database transaction acquiring a `PESSIMISTIC_WRITE` row lock (`SELECT ... FOR UPDATE`) on the target `Lead` scoped by `organization_id`.
2. **Idempotent Retry:** If `lead.status == CONVERTED` and `lead.converted_customer_id != null`, the endpoint returns the existing linked `Customer` with `alreadyConverted = true` and HTTP `200 OK`, preventing duplicate customers on network retries or double submissions.
3. **Invalid States:** Leads in `LOST` status cannot be converted (`422 Unprocessable Entity`).
4. **Duplicate Email Handling:** Within an organization, `customers` enforces uniqueness on `LOWER(email)` (when `email IS NOT NULL`). If a fresh lead's email matches an existing customer in the same organization:
   - By default (`linkExistingCustomer = false`), conversion returns `409 Conflict` so an existing account is not silently duplicated or overwritten.
   - When `linkExistingCustomer = true`, the lead links to that existing customer record without creating a duplicate.

### Follow-Up Dates & Notes
- Both `leads` and `customers` support optional `follow_up_date` (`DATE`) and summary `notes`.
- Timestamped follow-up notes are stored in `crm_notes` scoped by `(organization_id, entity_type, entity_id)` with author metadata (`author_id`, `author_name`). Adding a note with a `follow_up_date` updates the parent entity's `follow_up_date`.

---

## 2. Dashboard Metrics Definitions (`GET /api/v1/dashboard/summary`)

All queries filter strictly by `organization_id` from `TenantPrincipal`.

| Metric | Source & Formula | Date Range | Status in Prompt 03 |
|---|---|---|---|
| `totalCustomers` | `COUNT(c.id)` from `customers` where `organization_id = :orgId` | All-time | Active (live PostgreSQL) |
| `openLeads` | `COUNT(l.id)` from `leads` where `organization_id = :orgId` and `status IN ('NEW','CONTACTED','QUALIFIED','PROPOSAL')` | All-time | Active (live PostgreSQL) |
| `leadsByStage` | `COUNT(l.id)` grouped by `status` from `leads` where `organization_id = :orgId` | All-time | Active (live PostgreSQL) |
| `salesOrdersByStatus` | `COUNT(o.id)` grouped by `status` from `sales_orders` where `organization_id = :orgId` and `created_at` in period | `[from, to]` UTC | Pending Prompt 05 (`available = false`) |
| `revenue` | `SUM(i.total_amount)` across issued/paid non-voided invoices whose order is not `CANCELLED` within `[from, to]` UTC (avoids double-counting orders + invoices + payments) | `[from, to]` UTC (default last 30 days) | Pending Prompt 05 (`available = false`) |
| `outstandingInvoiceBalance` | `SUM(i.total_amount - COALESCE(paid, 0))` across non-voided, unpaid/partially-paid invoices in `organization_id` | Current snapshot | Pending Prompt 05 (`available = false`) |
| `lowStockProductsCount` | `COUNT(p.id)` from `products` where `organization_id = :orgId` and `active = true` and `stock_quantity <= low_stock_threshold` | Current snapshot | Pending Prompt 04 (`available = false`) |
# Dashboard & CRM Architecture Specification

**Phase:** Prompt 03  
**Status:** Active Specification

## 1. CRM Domain Model & Rules

### Lead Pipeline Statuses
Allowed values: `NEW`, `CONTACTED`, `QUALIFIED`, `PROPOSAL`, `CONVERTED`, `LOST`.

- **Open stages:** `NEW`, `CONTACTED`, `QUALIFIED`, `PROPOSAL`
- **Terminal stages:** `CONVERTED`, `LOST`

#### Allowed Manual Transitions (`PATCH /api/v1/crm/leads/{id}`)
- `NEW` → `CONTACTED`, `QUALIFIED`, `PROPOSAL`, `LOST`
- `CONTACTED` → `QUALIFIED`, `PROPOSAL`, `LOST`
- `QUALIFIED` → `PROPOSAL`, `LOST`
- `PROPOSAL` → `QUALIFIED`, `LOST`
- `LOST` → `CONTACTED`, `QUALIFIED` (re-opening a lost lead)
- `CONVERTED` → terminal (cannot manually revert to an open or lost status)
- Transitioning into `CONVERTED` via `PATCH` is rejected (`422`); conversion must execute through `POST /api/v1/crm/leads/{id}/convert`.

### Lead-to-Customer Conversion & Retry Safety
1. Conversion runs inside a single database transaction acquiring a `PESSIMISTIC_WRITE` row lock (`SELECT ... FOR UPDATE`) on the target `Lead` scoped by `organization_id`.
2. **Idempotent Retry:** If `lead.status == CONVERTED` and `lead.converted_customer_id != null`, the endpoint returns the existing linked `Customer` with `alreadyConverted = true` and HTTP `200 OK`, preventing duplicate customers on network retries or double submissions.
3. **Invalid States:** Leads in `LOST` status cannot be converted (`422 Unprocessable Entity`).
4. **Duplicate Email Handling:** Within an organization, `customers` enforces uniqueness on `LOWER(email)` (when `email IS NOT NULL`). If a fresh lead's email matches an existing customer in the same organization:
   - By default (`linkExistingCustomer = false`), conversion returns `409 Conflict` so an existing account is not silently duplicated or overwritten.
   - When `linkExistingCustomer = true`, the lead links to that existing customer record without creating a duplicate.

### Follow-Up Dates & Notes
- Both `leads` and `customers` support optional `follow_up_date` (`DATE`) and summary `notes`.
- Timestamped follow-up notes are stored in `crm_notes` scoped by `(organization_id, entity_type, entity_id)` with author metadata (`author_id`, `author_name`). Adding a note with a `follow_up_date` updates the parent entity's `follow_up_date`.

---

## 2. Dashboard Metrics Definitions (`GET /api/v1/dashboard/summary`)

All queries filter strictly by `organization_id` from `TenantPrincipal`.

| Metric | Source & Formula | Date Range | Status in Prompt 03 |
|---|---|---|---|
| `totalCustomers` | `COUNT(c.id)` from `customers` where `organization_id = :orgId` | All-time | Active (live PostgreSQL) |
| `openLeads` | `COUNT(l.id)` from `leads` where `organization_id = :orgId` and `status IN ('NEW','CONTACTED','QUALIFIED','PROPOSAL')` | All-time | Active (live PostgreSQL) |
| `leadsByStage` | `COUNT(l.id)` grouped by `status` from `leads` where `organization_id = :orgId` | All-time | Active (live PostgreSQL) |
| `salesOrdersByStatus` | `COUNT(o.id)` grouped by `status` from `sales_orders` where `organization_id = :orgId` and `created_at` in period | `[from, to]` UTC | Pending Prompt 05 (`available = false`) |
| `revenue` | `SUM(i.total_amount)` across issued/paid non-voided invoices whose order is not `CANCELLED` within `[from, to]` UTC (avoids double-counting orders + invoices + payments) | `[from, to]` UTC (default last 30 days) | Pending Prompt 05 (`available = false`) |
| `outstandingInvoiceBalance` | `SUM(i.total_amount - COALESCE(paid, 0))` across non-voided, unpaid/partially-paid invoices in `organization_id` | Current snapshot | Pending Prompt 05 (`available = false`) |
| `lowStockProductsCount` | `COUNT(p.id)` from `products` where `organization_id = :orgId` and `active = true` and `stock_quantity <= low_stock_threshold` | Current snapshot | Pending Prompt 04 (`available = false`) |

