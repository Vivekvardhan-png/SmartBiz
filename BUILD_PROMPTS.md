# CRM + ERP Platform: AI Agent Build Prompts

This document is the implementation prompt pack for building the AI-integrated, cloud-based CRM and ERP management platform.

## How to use this document

1. Place this file at the repository root as `BUILD_PROMPTS.md`.
2. Open the repository in Antigravity.
3. Give Antigravity the repository-orientation prompt provided separately in the chat.
4. Run the phases in order. Do not paste all phases into one coding request.
5. After each phase, inspect the diff, run the tests and verify the actual workflow before proceeding.

These prompts define **how to build the product**. The project PRD remains the source of truth for product requirements and scope. If a prompt conflicts with the PRD or an existing architectural decision, identify the conflict and ask for clarification rather than silently overriding either.

## Target stack

- **Frontend:** React + Vite + TypeScript
- **Backend:** Java 21 LTS + Spring Boot + Maven
- **Database:** PostgreSQL
- **Persistence:** Spring Data JPA + Hibernate
- **Database migrations:** Flyway
- **Validation:** Jakarta Bean Validation
- **Security:** Spring Security, or the existing managed identity provider if retaining it is the better fit
- **Testing:** JUnit 5, Mockito and Spring Boot testing tools; frontend tests using the repository's existing suitable tools
- **API documentation:** Springdoc OpenAPI
- **Forecasting:** Python + pandas + scikit-learn, introduced only when needed
- **AI assistant:** LLM provider API called from the backend through controlled, permission-checked tools

Verify compatible stable versions before implementation. Preserve a working existing stack unless a deliberate, documented migration is necessary.

---

# Prompt 00: Repository Audit + Design System Foundation

Act as a senior product engineer and frontend architect. Inspect the existing repository before changing anything.

## Product

Build an AI-integrated, cloud-based CRM and ERP management platform for small and medium businesses. It includes CRM, inventory, sales, finance, reporting, forecasting and a controlled AI assistant.

## First: audit the repository

- Identify the existing frontend framework, package manager, routes, components, design conventions, backend integrations and environment configuration.
- Identify what already works, what is incomplete and what must be preserved.
- Do not replace working code just because you prefer another approach.
- Present a short audit and implementation plan, then proceed with safe, in-scope changes.

## Design foundation

Use a modern, minimal enterprise design. Light theme by default, warm neutral surfaces, strong typography, restrained blue actions and clean data-dense layouts.

Proposed palette:

- App background: `#FAF9F6`
- Surface: `#FFFFFF`
- Secondary surface: `#F5F5F4`
- Border: `#E7E5E4`
- Primary text: `#1C1917`
- Secondary text: `#78716C`
- Primary action: `#2563EB`
- Hover action: `#1D4ED8`
- Success: `#15803D`
- Warning: `#B45309`
- Error: `#B91C1C`

Centralize design tokens for colors, typography, spacing, radii, shadows, focus rings and transitions. Use the existing styling solution if suitable.

Build reusable primitives for buttons, form fields, select menus, cards, badges, dialogs, tables, pagination, skeletons, alerts, toasts, empty states, breadcrumbs and page headers.

Create a consistent application shell with navigation for Dashboard, CRM, Operations, Finance, HR, Insights and Administration. Do not expose unfinished routes as functional modules. Include responsive navigation and role-aware visibility.

Every screen must support appropriate loading, empty, error and success states. Ensure keyboard navigation, visible focus, accessible labels and responsive layouts.

## Scope boundaries

- This phase is design and frontend foundation only.
- Do not implement business workflows yet.
- Do not redesign the database or backend.
- Do not add fake production data or silently replace failed API responses with fixtures.
- Preserve existing routes and working integrations.

## Acceptance criteria

1. Existing repository and stack are documented.
2. Design tokens are centralized.
3. Shared UI primitives are reusable and consistent.
4. App shell works on desktop, tablet and mobile.
5. Accessibility and basic interaction states are addressed.
6. Existing build and tests are run.
7. Report files changed, commands executed, actual results and unresolved issues.

Do not claim completion for anything that was not verified.

---

# Prompt 01: Spring Boot Backend Foundation

Act as a senior Java backend engineer. Establish the backend foundation for the existing CRM + ERP repository.

## Target stack

- Java 21 LTS
- Spring Boot 3.x or a compatible stable release supported by the chosen Java version
- Maven
- Spring Web for REST APIs
- Spring Data JPA + Hibernate
- PostgreSQL
- Flyway for schema migrations
- Jakarta Bean Validation
- Spring Security foundation
- JUnit 5, Mockito and Spring Boot testing tools
- Springdoc OpenAPI for API documentation

Verify compatibility and select mutually compatible stable dependency versions. Do not blindly upgrade existing dependencies.

## Before implementation

Inspect the repository and identify existing frontend/backend structure, conventions, environment files, tests and integrations. Preserve the frontend and avoid duplicating an existing backend.

## Implement

1. Establish a clean Spring Boot application structure.
2. Configure environment-based PostgreSQL connection settings.
3. Configure Flyway migrations and schema validation.
4. Establish consistent REST API conventions and JSON serialization.
5. Add request validation and centralized error responses.
6. Add health/readiness endpoints without exposing secrets or sensitive system details.
7. Configure CORS for explicitly configured frontend origins, not unrestricted origins.
8. Add structured logging without logging passwords, tokens or sensitive business data.
9. Document how to run the backend locally and configure environment variables.
10. Add a minimal API endpoint that proves the frontend can communicate with Java.

Use a modular monolith with business modules organized by domain. Keep controllers thin, business logic in services and database access in repositories. Use DTOs for API requests/responses rather than exposing JPA entities directly.

## Rules

- No microservices or message broker at this stage.
- No hardcoded database credentials or production secrets.
- No destructive database reset logic.
- Do not create every business module with placeholder CRUD merely to make the project look complete.
- Do not change the frontend framework.

## Acceptance criteria

- Backend compiles and starts.
- PostgreSQL connection is verified.
- Flyway migrations run successfully.
- Health endpoint returns the documented response.
- Validation and error handling have tests.
- API documentation is accessible in the configured development environment.
- Existing frontend still builds.
- Provide actual test/build results and the exact local run instructions.

---

# Prompt 02: Authentication + Multi-Tenant Authorization

Implement the identity and access foundation for the CRM + ERP application.

## First decision

Inspect the existing authentication setup. If Supabase Auth already exists, assess whether to retain it. Otherwise, implement a single well-defined authentication approach using Spring Security and an established, secure identity pattern. Do not create competing authentication systems.

Document the selected approach before making security-sensitive changes.

## Requirements

- User identity and organization membership.
- Organization creation and onboarding.
- Roles and permission checks.
- Protected API endpoints.
- Consistent unauthorized and forbidden responses.
- Secure logout/session or token handling appropriate to the selected approach.
- Configuration through environment variables.
- Tests for authorization boundaries.

Support an initial role model such as organization owner, administrator, manager and employee, but keep permissions explicit and maintainable.

## Tenant isolation

Every organization-owned business record must be scoped to the correct organization. Derive the effective organization from validated identity and membership, not from an untrusted organization ID supplied by the browser.

Enforce authorization in the backend for every protected operation, including reads, updates, deletes, exports and AI tool execution. Avoid insecure direct object references.

Plan database constraints and indexes around organization ownership. Where practical, enforce consistency with foreign keys and composite constraints. If Supabase Row Level Security is retained, document precisely which connections and access paths it protects. Do not assume RLS automatically protects Java's separate database connection.

## Security rules

- Never store plaintext passwords.
- Never trust frontend-only role checks.
- Never hardcode secrets or signing keys.
- Never log credentials or access tokens.
- Do not disable security to make tests pass.
- Do not add public registration or password-reset flows without defining their security requirements.

## Acceptance criteria

1. Authentication works using the selected approach.
2. Protected endpoints reject unauthenticated requests.
3. Users cannot access another organization's data by changing IDs.
4. Role restrictions are tested at the API/service level.
5. Organization membership is validated consistently.
6. Security configuration and local setup are documented.
7. Existing frontend integration is preserved or migrated deliberately with documented changes.

---

# Prompt 03: Dashboard + CRM Module

Implement the first usable business workflows in the existing React frontend and Java Spring Boot backend.

## Phase A: Dashboard

Create dashboard metrics using actual authorized database records:

- Total customers.
- Open leads and leads by pipeline stage.
- Sales orders by status.
- Revenue for a defined period.
- Outstanding invoice balance.
- Products below their stock threshold.

Define the meaning, date range and calculation rules for every metric. Avoid double-counting invoices, payments or cancelled orders. Use organization-scoped queries and suitable indexes.

## Phase B: CRM

Implement:

- Lead creation, listing, searching, filtering and pagination.
- Lead details, updates and status transitions.
- Customer records and contact information.
- Lead-to-customer conversion.
- Follow-up dates and notes.
- Validation and duplicate handling.

Define allowed lead statuses and conversion rules. Conversion must not accidentally create duplicate customers when retried. Use appropriate transactions and constraints.

## API and architecture

Use DTOs, validated request bodies, thin controllers, service-layer business logic and repositories. Use consistent pagination and error responses. Never expose internal entity fields unintentionally.

Connect the frontend to real APIs. Implement loading, empty, validation, success and failure states. Never silently show fixture data when an API call fails.

## Acceptance criteria

- CRM records persist across restarts.
- Search, filters and pagination work against backend data.
- Users can only access authorized organization records.
- Lead conversion follows defined rules and handles retries safely.
- Dashboard figures can be traced to documented queries and calculations.
- Tests cover validation, authorization, conversion and metric calculations.
- Frontend and backend build and test successfully.

Do not implement inventory or invoicing in this phase.

---

# Prompt 04: Inventory + Supplier Management

Implement inventory workflows using the existing React frontend, Java Spring Boot backend and PostgreSQL database.

## Features

- Product creation and editing.
- SKU and product search.
- Product categories and units of measure where required.
- Supplier records and supplier-product relationships.
- Current stock levels and configurable low-stock thresholds.
- Stock receipt, adjustment and other explicitly supported movement types.
- Stock movement history with actor, timestamp, quantity and reason.
- Low-stock dashboard integration.

## Data integrity

Do not treat current stock as an arbitrary editable number without an audit trail. Define a consistent stock model, including how the current quantity relates to recorded movements.

Prevent stock from becoming negative unless the product requirements explicitly permit it. Validate quantities and units. Prevent simultaneous orders or adjustments from overselling stock through appropriate transaction isolation, locking or conditional updates.

Ensure retries cannot apply the same movement twice when an idempotency mechanism is appropriate. Use database constraints and transactions for related writes.

All product, supplier and movement queries must respect organization boundaries.

## Frontend

Build searchable product and supplier tables, detail/edit forms, stock adjustment flows, movement history and clear low-stock indicators. Provide confirmation for consequential changes and accessible error messages.

## Acceptance criteria

- Products and suppliers persist in PostgreSQL.
- Every stock change has a traceable movement record.
- Concurrent stock changes cannot violate stock rules.
- Invalid quantities and unauthorized operations are rejected.
- Stock history reconciles with the documented stock model.
- Tests cover transaction rollback, concurrency-sensitive behavior, tenant isolation and validation.
- Existing CRM and dashboard behavior remains intact.

Do not implement purchase-order procurement or external warehouse integrations unless already explicitly required.

---

# Prompt 05: Sales + Finance Workflows

Implement sales orders, invoice generation and payment recording across the existing React and Java Spring Boot application.

## Sales orders

- Create an order for an existing customer.
- Add products and quantities.
- Validate product availability.
- Calculate line totals and order totals on the backend.
- Define allowed order statuses and transitions.
- Display order details and history.

## Invoices

- Generate an invoice from an eligible order.
- Store a stable invoice number and issue date.
- Store line-item descriptions, quantities, prices and applicable totals.
- Define how rounding, discounts and taxes are handled. Do not claim tax compliance that has not been implemented and verified.
- Prevent duplicate invoice generation from retries.
- Provide a readable invoice view and a printable or downloadable representation if feasible.

## Payments

- Record manually confirmed payments.
- Track payment date, amount, method and reference when available.
- Calculate paid and outstanding balances from recorded payment data.
- Validate overpayments, refunds and partial payments according to documented rules.
- Preserve an auditable payment history. Do not silently overwrite financial records.

## Integrity and security

Use database transactions for related changes. Inventory deductions must follow the established inventory model. Prevent duplicate deductions and invoices. Reconcile payment and invoice statuses using backend rules, not browser calculations.

Never use floating-point arithmetic for financial calculations. Use Java `BigDecimal` and suitable PostgreSQL numeric types with explicit precision and rounding rules.

All records and exports must be organization-scoped and permission-checked.

## Acceptance criteria

- Order totals are calculated and validated by Java.
- Eligible orders can produce invoices exactly once under retries.
- Partial and full payments produce correct balances.
- Failed transactions do not leave partially committed financial operations.
- Status transitions and audit history are tested.
- Invoice access cannot cross organization boundaries.
- No bank integration, automated payment collection or full statutory accounting system is invented.

Provide integration tests for the core order-to-invoice-to-payment journey.

---

# Prompt 06: Business Reports + Sales/Inventory Forecasting

Implement reporting first, then add a simple, measurable forecasting capability.

## Reporting

Create organization-scoped reports for:

- Sales over time.
- Orders by status.
- Outstanding invoices and recorded payments.
- Lead conversion and pipeline performance.
- Current stock and low-stock products.
- Stock movement history.

Use consistent date ranges, time-zone rules, pagination where appropriate and documented metric definitions. Ensure aggregate queries do not double-count joined records.

Provide suitable visualizations in React and CSV export where useful. Validate export permissions and prevent spreadsheet formula injection in exported text fields.

## Forecasting

Use Python, pandas and scikit-learn only where they provide real value. Keep Java as the primary application backend. If a separate Python service is justified, define a small, authenticated internal API with validated input and output schemas.

Start with a simple baseline forecast before introducing more complex models. Define the prediction target, forecast horizon, required historical data and minimum data-quality conditions.

Evaluate forecasts on a time-based holdout set. Report appropriate metrics such as MAE or WAPE when suitable, and document limitations. Never invent accuracy scores or present predictions as actual historical data.

If insufficient history exists, return a clear insufficient-data state rather than fabricating a forecast. Clearly distinguish actuals, estimates and predictions in the UI.

## Acceptance criteria

- Reports reconcile with underlying business records.
- Organization isolation is tested for reports and exports.
- CSV exports are valid and safely generated.
- Forecasting is evaluated against a baseline using time-ordered data.
- Forecasts expose their horizon and relevant limitations.
- The application continues to function when the forecasting service is unavailable.
- Tests cover report calculations, data validation and failure handling.

Do not add Kafka, a data lake or a complex ML platform. Keep this suitable for a small project team.

---

# Prompt 07: Controlled AI Assistant + Basic Automation

Implement a conversational business assistant that uses the existing Java backend and an LLM provider API.

## Core use cases

The assistant may help users:

- Summarize sales and business metrics.
- Find low-stock products.
- Retrieve customer or order information they are authorized to see.
- Explain invoice status and outstanding balances.
- Request supported business actions through explicit, validated tools.

## Tool architecture

Create a limited set of typed backend tools. Each tool must have a defined schema, validation rules, permission requirements and predictable response format.

Examples:

- `get_sales_summary(date_range)`
- `list_low_stock_products()`
- `get_order_status(order_id)`
- `get_outstanding_invoices(filters)`

For any write operation, require explicit user confirmation before execution. Revalidate identity, organization membership, permissions and business rules when the action executes. Do not rely on the LLM's statements about authorization.

## Security

- Never expose database credentials or unrestricted SQL access to the model.
- Never execute arbitrary generated code or shell commands.
- Treat retrieved documents, customer notes and user-supplied content as untrusted input.
- Defend against prompt injection by keeping system instructions separate from retrieved data and restricting tool capabilities.
- Redact sensitive fields where appropriate.
- Apply rate limits, request-size limits and timeouts.
- Do not log secrets or unnecessary personal data.
- Keep provider keys on the backend.

## Basic automation

Implement only a small number of deterministic workflows, such as a low-stock alert or a follow-up reminder, if the supporting infrastructure exists. Define trigger conditions, duplicate prevention, failure behavior and audit records.

## Acceptance criteria

- The assistant retrieves real data through approved Java services.
- Unauthorized tool calls are rejected even when requested by the model.
- No unrestricted database or server access is available to the LLM.
- Write operations require explicit confirmation and server-side validation.
- Provider failures and timeouts produce useful UI states.
- Tests cover permissions, malformed tool arguments, prompt injection scenarios and duplicate workflow triggers.

Do not claim that the model is always accurate. Show users the source records or relevant figures behind business answers wherever practical.

---

# Prompt 08: Full-Stack Release Audit + Hardening

Act as a skeptical senior engineer conducting a pre-release audit of the CRM + ERP platform. Inspect the entire repository and verify the current implementation rather than trusting previous agent reports.

## 1. Build and test

- Run frontend installation checks, linting, tests and production build using the existing package manager.
- Run Maven compilation and backend tests.
- Run integration tests against a suitable PostgreSQL test environment.
- Verify database migrations work on a clean database and upgrade an existing schema safely.
- Identify flaky tests, missing coverage and untested critical workflows.

## 2. Security

Audit authentication, authorization, session/token handling, CORS, input validation, secret handling, logging, dependency vulnerabilities and API exposure.

Test cross-organization access attempts, including reads, updates, deletes, reports, exports and AI tool calls. Check insecure direct object references and privilege escalation paths.

## 3. Business integrity

Test the complete workflow:

**Lead → Customer → Sales Order → Stock Deduction → Invoice → Payment → Dashboard/Reports**

Check transaction rollback, concurrent stock updates, duplicate submissions, invoice numbering, monetary rounding, payment balances and status transitions.

## 4. Operational readiness

Verify environment configuration, health checks, database connection behavior, logs, error messages, timeouts, backup procedures and documented restoration steps.

Do not claim backups work unless restoration has been tested. Do not expose stack traces, secrets or sensitive data to clients.

## 5. Frontend quality

Check navigation, responsive layouts, accessibility, loading and empty states, error handling, form validation, session expiry and API failure behavior. Ensure production screens do not silently use mock data.

## 6. Final report

Classify findings as Critical, High, Medium or Low. For each finding, include the affected file/module, evidence, impact and recommended fix.

Fix safe, in-scope issues and rerun relevant tests. Do not conceal failures, skip checks without reporting them or claim production readiness based solely on a successful build.

End with:

- Verified working features.
- Failed or skipped checks.
- Remaining security and correctness risks.
- Required environment variables and deployment steps.
- Known limitations.
- A release recommendation with evidence.

Do not add new features during this audit unless they are necessary to correct a confirmed defect.

---

# Recommended execution order

| Phase | Work |
|---|---|
| 00 | Repository audit and design system |
| 01 | Spring Boot backend foundation |
| 02 | Authentication, organizations and permissions |
| 03 | Dashboard and CRM |
| 04 | Inventory and suppliers |
| 05 | Sales orders, invoices and payments |
| 06 | Reports and forecasting |
| 07 | AI assistant and automation |
| 08 | Full release audit |

## Rules for every phase

1. Read the PRD and relevant existing code before editing.
2. Work only on the current phase.
3. Preserve existing working functionality and conventions.
4. Do not silently expand scope or replace technologies.
5. Do not use mock data as a fallback for failed production API calls.
6. Do not claim tests, builds or integrations passed unless they were actually run.
7. Report changed files, migrations, endpoints, tests, outcomes and unresolved issues.
8. If a significant requirement or architectural decision is ambiguous, explain the options and ask before making an irreversible choice.
9. Never disable security or weaken business rules merely to make a test pass.
10. After each phase, stop and provide a concise verification report. Wait for review before starting the next phase.
