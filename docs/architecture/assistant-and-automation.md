# Prompt 07: Controlled AI Assistant & Deterministic Automation

## Controlled Tool Gateway

- All LLM provider credentials live strictly on the Spring Boot backend (`AI_PROVIDER_API_KEY` via `ai.assistant.*`). No keys or unrestricted SQL access are exposed to the browser or model.
- `AssistantToolRegistry` exposes a fixed allowlist of typed tools with `additionalProperties: false` schema enforcement:
  - **Read tools** (`get_sales_summary`, `list_low_stock_products`, `get_customer_info`, `get_order_status`, `get_outstanding_invoices`) delegate strictly to existing organization-scoped Java services.
  - **Write tools** (`create_crm_follow_up_note`, `reschedule_lead_follow_up`) never execute inside the chat loop. Instead they persist a short-lived `PENDING` record in `assistant_action_proposals`.
- Every tool execution independently verifies `TenantPrincipal.hasPermission(requiredPermission)` on the backend regardless of what the LLM requests, and logs allowed, denied, and malformed calls to `assistant_audit_logs`.

## Prompt-Injection, Privacy & Resource Controls

- Retrieved CRM and order notes are stripped of control characters and delimiter tags via `PiiRedactor` and wrapped inside `<untrusted_business_data>...</untrusted_business_data>` markers separate from the `system` prompt.
- Email and phone fields passed into the model context are masked (`a***@domain.com`, `***-1234`).
- Request sizes (`<= 2000` characters, `<= 10` history turns), per-user sliding-window rate limits (`429 Too Many Requests`), tool-loop caps (`ai.assistant.max-tool-rounds`), and HTTP connect/read timeouts protect availability.
- Confirming a pending write action (`POST /api/v1/assistant/actions/{id}/confirm`) revalidates live organization membership, proposing user identity, proposal expiry, target permission (`PERM_CRM_WRITE`), and domain business rules.

## Deterministic Automation Workflows

- `LOW_STOCK_ALERT` and `FOLLOW_UP_REMINDER` run deterministically via `POST /api/v1/automation/workflows/run` or the optional `AutomationScheduler` (`automation.scheduler.enabled=true`).
- Duplicate alerts are prevented via organization-scoped `dedupe_key` checks (`uq_automation_alerts_open_dedupe` on PostgreSQL for open alerts and exact date keys for follow-up reminders).
- Every manual or scheduled execution records an immutable audit row in `automation_runs` (`COMPLETED` or `FAILED`).
