# 🗺️ Roadmap


## Goals

- A personal finance web app I can use every day: transactions, categories, monthly budgets, a dashboard and statistics.
- A clean, layered **Spring Boot** backend with a **PostgreSQL** database and a **React + TypeScript** frontend.
- Good engineering practice throughout: validation, consistent error handling, automated tests, API documentation, Docker and CI.
- An architecture ready for future authentication and multi-user support.

## Phases

### Backend
- [x] **Phase 0: Project setup.** Repository, README, roadmap, conventions.
- [x] **Phase 1: Backend skeleton.** Spring Boot app, PostgreSQL in Docker, Flyway, health check, CI.
- [x] **Phase 2: Database schema.** Migrations, constraints, default categories, JPA entities.
- [ ] **Phase 3: API foundations.** Error handling, validation, security/CORS config, Swagger UI.
- [ ] **Phase 4: Categories API.** Category CRUD with business rules.
- [ ] **Phase 5: Transactions API.** Transaction CRUD with validation.
- [ ] **Phase 6: Transaction queries.** Filtering, search, sorting, pagination.
- [ ] **Phase 7: Budgets API.** Monthly budgets with spent, remaining and percentage used.
- [ ] **Phase 8: Statistics and dashboard API.** Monthly totals, category breakdowns, dashboard summary.
- [ ] **Phase 9: Backend in Docker.** Dockerfile and Compose stack (database + backend).

### Frontend
- [ ] **Phase 10: Frontend foundation.** React + TypeScript + Vite, routing, layout, API client, tests.
- [ ] **Phase 11: Categories and transactions UI.** Lists, forms, filters, pagination.
- [ ] **Phase 12: Dashboard, budgets and statistics UI.** Charts and budget progress.
- [ ] **Phase 13: Full stack in Docker.** One command runs database, backend and frontend.

### Release
- [ ] **Phase 14: v1.0.** Hardening, documentation, screenshots, first release.

## After v1.0

### v1.1: Import and automatic categorization
- [ ] **Phase 15: Import pipeline.** Transaction source tracking (manual / import / bank) and a reusable pipeline: parse → normalize → deduplicate → categorize → save.
- [ ] **Phase 16: CSV import.** Upload a bank statement export, preview it, and import it without creating duplicates.
- [ ] **Phase 17: Categorization rules.** Keyword rules that map transaction descriptions to categories, with *Other* as the fallback.
- [ ] **Phase 18: Learning from corrections.** Recategorizing an imported transaction offers to create a rule for it.

### v1.2: Authentication
- [ ] **Phase 19: Authentication and multi-user support.** Required before any real bank account is connected.

### v1.3: Bank sync (Open Banking)
- [ ] **Phase 20: Bank connections.** Read-only account access through a PSD2 aggregator, including the consent flow, linked accounts, and consent expiry and renewal.
- [ ] **Phase 21: Automatic sync.** A scheduled job that pulls new transactions into the import pipeline.
- [ ] **Phase 22: Bank sync UI.** Connect a bank, see connection status, re-authorize expired consents.

## Later

Per-category budgets · recurring transactions · multi-currency
