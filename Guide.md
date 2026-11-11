# SmartBiz Setup & Testing Guide

Follow these steps to set up, run, and verify **SmartBiz** on your local machine.

---

## 1. Requirements

| Software | Version | Purpose | Required? | Download Link |
|---|---|---|---|---|
| **Java JDK** | 21 or newer | Compiles and runs the Spring Boot backend | **Mandatory** | https://adoptium.net/temurin/releases/?version=21 |
| **Node.js & npm** | Node 20+ | Installs packages and runs the React frontend | **Mandatory** | https://nodejs.org/ |
| **PostgreSQL** | 14 or newer | Stores all application data | **Mandatory** (to run app) | https://www.postgresql.org/download/ |
| **Python** | 3.11 or newer | Runs the forecasting microservice | **Optional** | https://www.python.org/downloads/ |
| **Maven** | Included | Already bundled as `backend/mvnw.cmd` (`mvnw` on macOS/Linux) | Not needed separately | — |

---

## 2. Environment Setup

Copy the sample environment files before running the project:

1. **Backend:** Copy `backend\.env.example` to `backend\.env`.
2. **Frontend:** Copy `frontend\.env.example` to `frontend\.env`.
3. **Forecasting (Optional):** Copy `forecasting\.env.example` to `forecasting\.env` only if you plan to run forecasting.

### Essential Variables (`backend/.env`)

| Variable | Required? | Purpose | Example Value |
|---|---|---|---|
| `DB_URL` | **Required** | PostgreSQL connection string | `jdbc:postgresql://localhost:5432/smartbiz_dev` |
| `DB_USERNAME` | **Required** | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | **Required** | PostgreSQL password | `your_postgres_password` |
| `JWT_SECRET` | **Required** | Random signing key (minimum **32 characters**) | Generate 64 hex chars (see below) |
| `CORS_ALLOWED_ORIGINS` | **Required** | Allowed frontend URL | `http://localhost:5173` |
| `FORECASTING_ENABLED` | Optional | Turn on Python forecasting integration (`false` by default) | `false` |
| `FORECASTING_API_KEY` | Optional | Shared secret if `FORECASTING_ENABLED=true` (make up any matching string) | `local-forecast-secret` |
| `AI_ASSISTANT_ENABLED` | Optional | Turn on AI assistant (`false` by default) | `false` |
| `AI_PROVIDER_API_KEY` | Optional | API key from OpenAI (or compatible provider) if AI assistant is enabled | `sk-...` |

> **Frontend (`frontend/.env`):** Keep `VITE_API_BASE_URL=http://localhost:8080`.  
> **Generate a valid `JWT_SECRET` in PowerShell:**  
> `-join ((1..32) | ForEach-Object { "{0:x2}" -f (Get-Random -Maximum 256) })`  
> *(macOS/Linux: `openssl rand -hex 32`)*

---

## 3. Database Setup

SmartBiz creates all database tables automatically when the backend starts (using Flyway), **as long as the database itself already exists**.

1. Make sure your **PostgreSQL** service is running on port `5432`.
2. Create the database named `smartbiz_dev` (matching the default `DB_URL` in `backend/.env`):

```powershell
# Windows PowerShell (adjust "18" to your installed PostgreSQL version if different)
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE smartbiz_dev;"
```
*(macOS/Linux: `psql -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE smartbiz_dev;"`)*

3. Put your PostgreSQL username and password into `backend\.env` (`DB_USERNAME` and `DB_PASSWORD`). When you start the backend, Flyway automatically initializes the schema.

---

## 4. Install Dependencies

Open PowerShell in the repository root folder:

### Frontend
```powershell
cd frontend
npm install
cd ..
```

### Backend
No manual dependency install is needed. The included Maven Wrapper (`backend\mvnw.cmd`) automatically downloads what Maven needs on first run.

### Forecasting Service *(Optional)*
Only run these commands if you want to enable forecasting:
```powershell
cd forecasting
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
cd ..
```
*(macOS/Linux: use `python3 -m venv .venv` and `./.venv/bin/python -m pip install -r requirements.txt`)*

---

## 5. Run the Project

Use **separate terminal windows** so each service stays running. Start the backend before using the frontend.

### 1. Start the Backend (Terminal 1)
```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```
*(macOS/Linux: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`)*
- **Local URL:** `http://localhost:8080`
- **Health Check:** `http://localhost:8080/actuator/health`
- **Stop:** Press `Ctrl + C`.

### 2. Start the Frontend (Terminal 2)
```powershell
cd frontend
npm run dev
```
- **Local URL:** Open `http://localhost:5173` in your browser.
- **Stop:** Press `Ctrl + C`.

### 3. *(Optional)* Start the Forecasting Service (Terminal 3)
Only needed if `FORECASTING_ENABLED=true` in `backend\.env`. Make sure `$env:FORECASTING_API_KEY` matches `FORECASTING_API_KEY` in `backend\.env`:
```powershell
cd forecasting
$env:FORECASTING_API_KEY = "local-forecast-secret"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```
*(macOS/Linux: `FORECASTING_API_KEY="local-forecast-secret" .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8001`)*
- **Local URL:** `http://localhost:8001/health`

---

## 6. Test the Project

### Backend Tests
```powershell
cd backend
.\mvnw.cmd test
```
> **Note:** Backend tests use an in-memory **H2** database by default, so **PostgreSQL does not need to be running** for `.\mvnw.cmd test`.

### Frontend Tests, Lint & Build
```powershell
cd frontend
npm run lint
npm run test
npm run build
```
*(Frontend tests run without needing the backend or PostgreSQL.)*

### Optional Forecasting Tests
```powershell
cd forecasting
.\.venv\Scripts\python.exe -m pytest -v
```

---

## 7. Verify Everything Works

Once both Backend and Frontend are running, do a quick smoke check in your browser:

- [ ] **Application opens:** Visiting `http://localhost:5173` redirects to the **Sign in to SmartBiz** page.
- [ ] **Registration works:** Click create account, fill in details (password >= 10 chars with uppercase, lowercase, and a number), and confirm you land on the **Dashboard**.
- [ ] **Login works:** Log out and sign back in with the same email and password.
- [ ] **Dashboard loads:** Metrics cards and navigation sidebar (`SmartBiz`) render with no error banners.
- [ ] **CRM works:** Open **CRM**, create a lead, and convert it into a customer.
- [ ] **Inventory works:** Open **Operations**, create a product with initial stock, and record a stock movement.
- [ ] **Finance works:** Open **Finance**, create and confirm a sales order (stock decreases), generate an invoice, and record a payment.
- [ ] **Reports load:** Open **Insights** and confirm **Business Reports** displays totals and exports CSV cleanly.
- [ ] **Optional services fallback cleanly:** When disabled (`false`), the **Forecasting** tab shows an `UNAVAILABLE` notice and **AI Assistant** shows a `PROVIDER_UNAVAILABLE` notice without breaking the app.

---

## 8. Common Issues

| Problem | Fix |
|---|---|
| **Database connection failure** | Check that PostgreSQL is running on port `5432`, database `smartbiz_dev` exists (`CREATE DATABASE smartbiz_dev;`), and `DB_USERNAME` / `DB_PASSWORD` in `backend\.env` are correct. |
| **Port already in use (`8080` or `5173`)** | Stop the old terminal process using that port (`Get-NetTCPConnection -LocalPort 8080` in PowerShell to find its PID, then `Stop-Process -Id <PID> -Force`). |
| **Incorrect or missing environment variables** | Confirm `backend\.env` exists, `JWT_SECRET` is at least 32 characters, and you started the backend with `"-Dspring-boot.run.profiles=local"`. |
| **Frontend cannot connect to backend** | Make sure the backend is running on `8080`, `frontend\.env` has `VITE_API_BASE_URL=http://localhost:8080`, and `CORS_ALLOWED_ORIGINS` in `backend\.env` includes `http://localhost:5173`. |
| **Java or Node version mismatch** | Check `java -version` (needs **21+**) and `node -v` (needs **20+**). Install the required version and open a new terminal. |

---

## 9. Quick Start

Run from the repository root (`SmartBiz`):

```powershell
# 1. Create database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE smartbiz_dev;"

# 2. Setup env files (edit backend\.env with your DB_PASSWORD and 32+ char JWT_SECRET)
Copy-Item backend\.env.example backend\.env
Copy-Item frontend\.env.example frontend\.env

# 3. Install frontend dependencies & run all tests
cd frontend; npm install; npm run lint; npm run test; npm run build; cd ..
cd backend; .\mvnw.cmd test; cd ..

# 4. Run Backend (Terminal 1) and Frontend (Terminal 2)
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
# In a second terminal:
cd frontend; npm run dev
```
