# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application (port 8000)
mvn spring-boot:run

# Run tests
mvn test

# Build fat jar
mvn package -DskipTests

# Trigger org sync manually
curl -X POST http://localhost:8000/api/admin/sync

# Check current user (requires session cookie)
curl http://localhost:8000/api/me

# H2 console (browser only)
# http://localhost:8000/h2-console  JDBC URL: jdbc:h2:mem:iamdb
```

## Architecture

Two independent concerns live side by side:

### 1. SSO Flow (browser-based)
```
oa.html  →  /oauth2/authorization/keycloak  →  Keycloak  →  /login/oauth2/code/keycloak  →  dashboard.html
```
- `SecurityConfig` controls which URLs require auth and configures OAuth2 client + JWT resource server simultaneously.
- Session-based for browser flows; JWT bearer for API clients — both active at once via `SessionCreationPolicy.IF_REQUIRED`.
- `GET /api/me` is `permitAll` intentionally — returns 401 body rather than redirect, so the frontend can detect login state without being intercepted.
- Keycloak client config lives in `application.yml` under `spring.security.oauth2.client.registration.keycloak`; `client-id` and `client-secret` must be injected via environment variables (not hardcoded).

### 2. Org Sync
```
OrgSource (interface)  →  OrgSyncService  →  UserRepository / DepartmentRepository  →  H2
                ↑
         MockOrgSource (dev stub)
```
- `OrgSource` is the extension point — add real integrations (LDAP, Dingtalk, SCIM) by implementing this interface; `OrgSyncService` picks them all up automatically via `List<OrgSource>` injection.
- `OrgSyncService.syncAll()` does upsert by `externalId` (not by username/email). Topological sort ensures parent departments are persisted before children.
- `OrgSyncScheduler` drives the cron; interval configured by `ldap.sync-cron` in `application.yml`.

### Frontend
Three static pages served by Spring Boot (`src/main/resources/static/`):
- `index.html` — login landing, checks `/api/me` via `auth.js`
- `oa.html` — simulated OA portal with an SSO entry point tile pointing to `/oauth2/authorization/keycloak`
- `dashboard.html` — post-login view showing OIDC claims and synced users from `/api/users`

`auth.js` is shared across pages — `fetchMe()` and `renderAuthStatus()` are the two exported helpers.

### Layer conventions
| Layer | Package | Responsibility |
|---|---|---|
| Controller | `controller/` | Validate input → call service → return response |
| Service | `sync/` | Business logic, transactions |
| Repository | `repository/` | Data access, Spring Data JPA |
| Entity | `entity/` | DB table mapping |
| DTO | `dto/` | External system shapes (anti-corruption layer) |

## Key config

- App runs on **port 8000** (not 8080).
- H2 is in-memory — data resets on restart; switch datasource in `application.yml` to use Postgres/MySQL.
- `spring.jpa.hibernate.ddl-auto: update` auto-creates tables from `@Entity` — use Flyway for any production schema.
- LDAP config block (`ldap.*`) is read as a custom property group; not bound to Spring's auto-config — wire it manually if needed.
