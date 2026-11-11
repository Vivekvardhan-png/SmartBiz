# Deployment, Configuration, Health & Backup Runbook

## 1. Required Environment Variables

### Backend (`backend/`)
- `DB_URL`: JDBC URL for PostgreSQL (e.g. `jdbc:postgresql://localhost:5432/smartbiz_dev`)
- `DB_USERNAME`: PostgreSQL application user
- `DB_PASSWORD`: PostgreSQL password (never commit or embed in `application.yml`)
- `JWT_SECRET`: High-entropy HS256 signing secret, minimum 32 bytes (validated on startup by `JwtProperties`)
- `JWT_EXPIRATION_SECONDS`: Access token lifetime in seconds (default `3600`)
- `CORS_ALLOWED_ORIGINS`: Comma-separated explicit allowed origins (wildcard `*` rejected; required in `prod` profile with no localhost default)
- Optional forecasting service: `FORECASTING_ENABLED` (`false` default), `FORECASTING_URL`, `FORECASTING_API_KEY`
- Optional AI assistant & automation: `AI_ASSISTANT_ENABLED` (`false` default), `AI_PROVIDER_BASE_URL`, `AI_PROVIDER_API_KEY`, `AI_MODEL`, `AI_RATE_LIMIT_PER_MINUTE`, `AUTOMATION_SCHEDULER_ENABLED` (`false` default)

### Frontend (`frontend/`)
- `VITE_API_BASE_URL`: Backend API origin (e.g. `http://localhost:8080` in local dev)

### Optional Forecasting Service (`forecasting/`)
- `FORECASTING_API_KEY`: Shared secret matching `FORECASTING_API_KEY` in backend (mandatory for `/internal/v1/*`; verified with constant-time `hmac.compare_digest`)

## 2. Production Profile & Startup

Activate the hardened Spring profile (`application-prod.yml`) which disables OpenAPI/Swagger endpoints, disables stack-trace/message exposure, sets safe `INFO`/`WARN` log levels, and requires explicit `CORS_ALLOWED_ORIGINS`:

```powershell
java -jar target/nexusops-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Health & readiness probes:
- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

## 3. PostgreSQL Backup & Tested Restore Procedure

### Backup (`pg_dump`)
```powershell
pg_dump -h <DB_HOST> -p 5432 -U <DB_USER> -Fc -f smartbiz_backup.dump <DB_NAME>
```

### Restore & Verify (`pg_restore`)
```powershell
createdb -h <DB_HOST> -p 5432 -U <DB_USER> <TARGET_RESTORE_DB>
pg_restore -h <DB_HOST> -p 5432 -U <DB_USER> -d <TARGET_RESTORE_DB> --clean --if-exists smartbiz_backup.dump
psql -h <DB_HOST> -p 5432 -U <DB_USER> -d <TARGET_RESTORE_DB> -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

