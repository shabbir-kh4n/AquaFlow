# HydroLedger

A Smart Water Usage Monitoring & Automated Billing Management platform for apartment communities. Tracks per-household water meter readings, computes tiered-tariff bills, manages billing cycles, and apportions shared common-area costs.

> Recently renamed from AquaFlow — the rename covers the UI, `package.json`, `pom.xml`, the Java package (now `com.hydroledger.backend`), and the Maven `artifactId` (now `hydroledger-backend`). The repo folder/git remote are unchanged for now.

## Stack

- **Backend**: Spring Boot 4.1.1, Java 21, Spring Security 7.1.1 + JWT, Spring Data JPA, PostgreSQL, Flyway, Apache POI, Apache PDFBox
- **Frontend**: React 19 + Vite 8, React Router, Axios, Recharts, react-i18next
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

`.env` needs `VITE_API_URL=http://localhost:8080/api` (the `.env.example` default). Keep it on `localhost` rather than your PC's LAN IP, which the router can reassign at any time. To test on an Android phone over USB, forward the ports instead (`adb reverse tcp:5175 tcp:5175` and `adb reverse tcp:8080 tcp:8080`, plus `tcp:4173` for `npm run preview`), then open `http://localhost:5175` on the phone.

### Translations

The whole UI is available in 110 languages via react-i18next. English sources live in `frontend/public/locales/en/{landing,common,resident,admin,platform}.json`; every other language is generated from them. After adding or changing English strings, delete the affected locale files (or add new keys) and regenerate:

```
cd frontend
node scripts/generate-translations-google.mjs     # fast, free (unofficial Google Translate endpoint)
node scripts/generate-translations.mjs --only=hi  # slower, higher quality (Gemini, uses gemini.api.key)
```

Both scripts only generate files that don't exist yet.

## Project status

Phase 1 (auth, apartment/household/meter schema, resident & admin dashboards) and Phase 2 (tiered tariff billing engine, billing cycle management, bulk water purchase tracking, shared common-area cost apportionment, alert engine) are feature-complete. Phase 3's remaining items — PDF invoice generation, transactional email, and a real household-vs-apartment consumption comparison view — are now done too. The whole app (all three roles) is also available in 110 languages, and the AI chat assistant and recommendations reply in the selected language. See `STATUS.md` and `ROADMAP.md` (gitignored, local reference docs) for the full detail and next steps.

## Repo layout

```
backend/    Spring Boot API
frontend/   Vite + React app
docs/       Deeper technical reference (entities/endpoints/components) — gitignored
```
