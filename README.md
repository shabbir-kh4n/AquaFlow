# HydroLedger

A Smart Water Usage Monitoring & Automated Billing Management platform for apartment communities. Tracks per-household water meter readings, computes tiered-tariff bills, manages billing cycles, and apportions shared common-area costs.

> Recently renamed from AquaFlow — the rename covers the UI, `package.json`, `pom.xml`, the Java package (now `com.hydroledger.backend`), and the Maven `artifactId` (now `hydroledger-backend`). The repo folder/git remote are unchanged for now.

## Stack

- **Backend**: Spring Boot 4.1.1, Java 21, Spring Security 7.1.1 + JWT, Spring Data JPA, PostgreSQL, Flyway, Apache POI
- **Frontend**: React 19 + Vite 8, React Router, Axios, Recharts
- **Database**: PostgreSQL (`smart_water`), schema managed via Flyway migrations (`backend/src/main/resources/db/migration/`)

## Getting started

### Backend (port 8080)

```
cd backend
copy src\main\resources\application-local.properties.example src\main\resources\application-local.properties
```

Fill in your local Postgres password (and mail credentials, if needed) in `application-local.properties`, ensure PostgreSQL is running with a `smart_water` database, then:

```
./mvnw.cmd spring-boot:run
```

### Frontend (port 5175)

```
cd frontend
copy .env.example .env
npm install
npm run dev
```

`.env` needs `VITE_API_URL=http://localhost:8080`.

## Project status

Phase 1 (auth, apartment/household/meter schema, resident & admin dashboards) and Phase 2 (tiered tariff billing engine, billing cycle management, bulk water purchase tracking, shared common-area cost apportionment) are feature-complete. See `STATUS.md` and `ROADMAP.md` (gitignored, local reference docs) for the full detail and next steps.

## Repo layout

```
backend/    Spring Boot API
frontend/   Vite + React app
docs/       Deeper technical reference (entities/endpoints/components) — gitignored
```
