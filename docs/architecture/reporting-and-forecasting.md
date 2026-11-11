# Business Reports & Forecasting Architecture

**Phase:** Prompt 06  
**Status:** Active Specification

## 1. Organization-Scoped Reporting

All report and export endpoints live under `/api/v1/insights/reports/**`, require `PERM_INSIGHTS_READ` (`OWNER`, `ADMIN`, `MANAGER`), and scope every query to `TenantPrincipal.organizationId()`.

- **Time Zone & Date Range:** Date boundaries use UTC (`[from, to]` inclusive; default last 30 UTC days). Timestamped columns (`stock_movements.created_at`, `payments.created_at`) filter `[from 00:00:00Z, (to + 1 day) 00:00:00Z)`.
- **Anti-Double-Counting Rules:**
  - `sales-over-time` aggregates directly on `invoices` (`ISSUED`, `PARTIALLY_PAID`, `PAID`) without joining `invoice_items` or `payments`.
  - `receivables` separates invoice balances (`ISSUED`, `PARTIALLY_PAID`) from ledger entries (`PAYMENT` vs `REFUND`).
- **CSV Export Formula-Injection Mitigation (`CsvSanitizer`):**
  - Any text field starting with `=`, `+`, `-`, `@`, `\t`, or `\r` (even after leading whitespace) is prefixed with `'` before RFC-4180 quoting.

## 2. Forecasting Service (`forecasting/`)

- **Role:** Small, authenticated Python (`pandas` + `scikit-learn`) internal FastAPI service invoked by Java over HTTP with `X-Internal-Api-Key` and strict timeouts (`2s` connect, `5s` read).
- **Resilience:** When the forecasting service is disabled or unreachable, the Java backend returns `status = "UNAVAILABLE"` and `serviceAvailable = false` while all CRM, inventory, finance, and reporting workflows continue normally.
- **Data-Quality & Minimum History Gate:**
  - Requires at least **14 calendar days** of history and **7 non-zero observation days**. Otherwise returns `status = "INSUFFICIENT_DATA"` without fabricating forecasts or accuracy metrics.
- **Baseline & Holdout Evaluation:**
  - **Baseline:** 7-day trailing moving average.
  - **Model:** `scikit-learn` `Ridge(alpha=1.0)` using time trend, cyclical day-of-week (`sin`/`cos`), and trailing 7-day rolling mean, clipped at `>= 0`.
  - **Holdout:** Chronological tail split (last `min(7, max(3, floor(N * 0.2)))` days). Reports `MAE` and `WAPE` for both baseline and Ridge model without inventing scores.
  - **Limitations:** Short-history trend/weekly seasonality only; does not model promotions, price changes, or external shocks. Forecasts are explicitly distinguished from actuals in the UI.

