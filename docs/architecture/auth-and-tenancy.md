# Authentication & Multi-Tenant Authorization Architecture

**Phase:** Prompt 02  
**Status:** Active Design Specification

## 1. Selected Authentication Approach

No existing identity provider (such as Supabase Auth) is present in the repository. To keep identity and authorization cohesive inside the Java modular monolith without competing authentication systems, NexusOps uses **Spring Security 6** with:

1. **BCrypt Password Hashing (`BCryptPasswordEncoder`, strength 12):** Plaintext passwords are never stored, logged, or returned.
2. **Signed Stateless JWT Access Tokens (HMAC-SHA256 via `JWT_SECRET` env var):**
   - Claims: `sub` (`userId`), `jti` (`sessionId`), `org` (`organizationId`), `iat`, `exp`.
   - Tokens are sent via `Authorization: Bearer <token>`.
3. **Database-Backed Session Lifecycle (`auth_sessions`):**
   - Every login, registration, and organization switch creates a row in `auth_sessions` with `expires_at` and `revoked_at`.
   - `JwtAuthenticationFilter` verifies the JWT signature and expiry **and** confirms the `auth_sessions` row is active (`revoked_at IS NULL` and `expires_at > now()`) and that the user is enabled and currently belongs to the session's `organization_id` in `organization_members`.
   - `POST /api/v1/auth/logout` revokes the `auth_sessions` row immediately so logged-out tokens cannot be reused.

## 2. Multi-Tenant Isolation & Authorization Boundaries

- **Authoritative Organization Context:** Every request's `TenantPrincipal` is derived server-side from the validated JWT session (`auth_sessions.organization_id`) and live membership row (`organization_members`), **never** from an unverified browser-supplied `organizationId` parameter or frontend role state.
- **Organization Switching:** Users belonging to multiple organizations switch context through `POST /api/v1/auth/switch-organization`, which verifies membership in `organization_members`, revokes the previous session, and issues a new token bound to the target organization. Attempting to switch to a non-member organization returns `403 Forbidden`.
- **Database Constraints & Indexes:**
  - All organization-owned records carry `organization_id` referencing `organizations(id)` and are indexed on `organization_id`.
  - Every repository query for organization-owned entities includes `organization_id` in its predicate so cross-tenant ID tampering returns `404 Not Found` or `403 Forbidden`.
  - Note on PostgreSQL RLS: Row Level Security is **not** relied upon because the Spring Boot backend connects through a single application database role (`DB_USERNAME`). Isolation is enforced consistently in the Spring Security filter, `TenantContext`, service layer, and repository queries.

## 3. Roles & Explicit Permissions

| Role | Description | Granted Permissions |
|---|---|---|
| `OWNER` | Organization creator / primary owner | `ORG_MANAGE`, `MEMBERS_READ`, `MEMBERS_MANAGE`, `CRM_READ`, `CRM_WRITE`, `INVENTORY_READ`, `INVENTORY_WRITE`, `FINANCE_READ`, `FINANCE_WRITE`, `INSIGHTS_READ`, `AI_USE` |
| `ADMIN` | Organization administrator | `ORG_MANAGE`, `MEMBERS_READ`, `MEMBERS_MANAGE`, `CRM_READ`, `CRM_WRITE`, `INVENTORY_READ`, `INVENTORY_WRITE`, `FINANCE_READ`, `FINANCE_WRITE`, `INSIGHTS_READ`, `AI_USE` |
| `MANAGER` | Department / operational manager | `MEMBERS_READ`, `CRM_READ`, `CRM_WRITE`, `INVENTORY_READ`, `INVENTORY_WRITE`, `FINANCE_READ`, `FINANCE_WRITE`, `INSIGHTS_READ`, `AI_USE` |
| `EMPLOYEE` | Standard team member | `CRM_READ`, `CRM_WRITE`, `INVENTORY_READ`, `INVENTORY_WRITE` |

Additional role safety rules:
- Only an `OWNER` may assign or revoke the `OWNER` role.
- An organization must always retain at least one `OWNER`; demoting or removing the last `OWNER` is rejected (`422 Unprocessable Entity`).

## 4. Public Onboarding & Endpoint Security Rules

- `POST /api/v1/auth/register` creates a new user account, a new organization, and an initial `OWNER` membership atomically. It **never** adds a user to an existing organization; existing organizations add members only via authenticated `OWNER`/`ADMIN` calls to `POST /api/v1/organizations/current/members`.
- Password validation requires at least 10 characters with uppercase, lowercase, and numeric characters.
- Password-reset flows are intentionally excluded until verified outbound email infrastructure is configured.
- Invalid credentials return `401 Unauthorized` with a generic error message that does not reveal whether an email exists.
- Missing/invalid authentication returns `401 Unauthorized`; insufficient role/permission or cross-tenant access returns `403 Forbidden` (via `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`).

## 5. Required Environment Variables

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL JDBC connection
- `CORS_ALLOWED_ORIGINS` — explicit comma-separated frontend origins
- `JWT_SECRET` — high-entropy HMAC-SHA256 signing secret (minimum 32 bytes / 256 bits)
- `JWT_EXPIRATION_SECONDS` — token/session TTL in seconds (default `3600`)

