# CRM + ERP Platform

AI-integrated, cloud-based CRM and ERP management platform for small and medium businesses.

## Current Phase Status

- **Phase 00 (Completed):** Repository audit, centralized design tokens, shared UI primitives, and responsive role-aware application shell in `frontend/`.
- **Phase 01 (Completed & Verified against PostgreSQL 18):** Spring Boot modular monolith backend foundation in `backend/` with environment-based PostgreSQL config, Flyway migrations, validation & centralized error handling, health/readiness endpoints, explicit CORS, OpenAPI docs, and Maven Wrapper (`mvnw.cmd`).
- **Phase 02 (Completed):** Spring Security Bearer JWT authentication + revocable PostgreSQL `auth_sessions`, BCrypt password hashing, organization onboarding, multi-tenant isolation (`TenantPrincipal`/`TenantContext`), role/permission checks, and React login/register/administration UI in `frontend/`.
- **Phase 03 (Completed):** Organization-scoped Dashboard metrics (`/api/v1/dashboard/summary`) and CRM Lead/Customer pipeline, search/filter/pagination, follow-up notes, and idempotent lead-to-customer conversion (`V3__crm_module.sql`) with connected React UI (`DashboardPage`, `CrmPage`).
- **Phases 04–08 (Upcoming):** Inventory & Suppliers, Sales & Finance, Reporting & Forecasting, AI Assistant & Automation, and Release Hardening.

See `BUILD_PROMPTS.md` for the phased implementation roadmap and `docs/architecture/auth-and-tenancy.md` for the security architecture.

## Repository Structure

```text
.
├── BUILD_PROMPTS.md        # Phased engineering implementation guide
├── README.md               # Repository overview and setup instructions
├── frontend/               # React + Vite + TypeScript frontend application
│   ├── .env.example
│   └── src/
│       ├── app/            # Navigation metadata & shell role-preview context
│       ├── components/
│       │   ├── shell/      # Responsive AppShell with role-aware navigation
│       │   └── ui/         # Reusable design-system primitives
│       ├── design-system/  # Centralized CSS/TS tokens and global styles
│       └── pages/          # Module foundation placeholders & Design System showcase
└── backend/                # Java 21 + Spring Boot 3.3 + Maven modular monolith
    ├── .env.example
    ├── mvnw / mvnw.cmd     # Self-contained Maven Wrapper
    ├── pom.xml
    └── src/
        ├── main/java/com/nexusops/platform/
        │   ├── common/     # API envelopes (ApiResponse, PagedResponse, ErrorResponse) & GlobalExceptionHandler
        │   ├── config/     # CORS, Jackson, OpenAPI, Security foundation
        │   └── health/     # /api/v1/ping connectivity endpoint
        └── main/resources/
            ├── application.yml
            ├── application-local.yml
            └── db/migration/V1__init_schema.sql
```

## Running the Frontend Locally

Prerequisites: Node.js 20+ (verified with Node v24.11.1 and npm 11.12.0).

```powershell
cd frontend
npm install
npm run dev
```

Frontend verification:

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

## Running the Backend Locally

Prerequisites:
- Java 21+ JDK (verified compiling target `21` on Java `25.0.1 LTS`)
- Local PostgreSQL 14+ database (not yet provisioned in this environment; required before starting the live server)

1. Configure environment variables:

```powershell
cd backend
Copy-Item .env.example .env
# Edit .env with your PostgreSQL DB_URL, DB_USERNAME, DB_PASSWORD, CORS_ALLOWED_ORIGINS, JWT_SECRET, and JWT_EXPIRATION_SECONDS
```

2. Run backend unit & slice tests (uses H2 in-memory; does not require PostgreSQL):

```powershell
cd backend
.\mvnw.cmd test
```

3. Start the backend against PostgreSQL (Flyway runs `V1__init_schema.sql` automatically):

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

4. Key local URLs once running:
- Ping endpoint: `http://localhost:8080/api/v1/ping`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
