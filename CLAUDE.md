# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Maven, with `io.helidon.applications:helidon-se` (4.5.x) as parent. Helidon 27+ requires Java 27, so stay on 4.x while on Java 25.

```sh
mvn package                                   # compile, test, build target/authorization-server.jar + target/libs/
mvn test -Dtest=AuthorizationApiTest          # single test class
mvn test -Dtest=AuthorizationApiTest#returns401WithoutToken   # single test method
java -jar target/authorization-server.jar     # run (port 8080, config in src/main/resources/application.yaml)
mvn package && docker build -t authorization-server .   # image is assembled from target/, so build with Maven first
mvn package && docker compose up --build      # local stack: PostgreSQL, Keycloak (localhost:8180), server (localhost:8080)
```

Templates in `src/main/jte` are precompiled by `jte-maven-plugin` (`generate-sources`) and loaded with `TemplateEngine.createPrecompiled`: the jlinked runtime has no Java compiler, so only `jte-runtime` is a dependency.

Persistence tests start `postgres:18-alpine` through Testcontainers 2.x, so Docker must be running. With Colima (the local setup), Testcontainers needs these three variables, otherwise it cannot find Docker or fails with `Could not connect to Ryuk at null:...`:

```sh
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_HOST_OVERRIDE=localhost
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`, `build:`, `ci:`; optional scope, e.g. `feat(api): ...`).

Base package: `net.bakaar` (application code under `net.bakaar.authz`). Entry point: `net.bakaar.authz.Main`; `Main.start(Config)` is what tests call to boot a server (use `server.port=0`).

## Container image and Leyden AOT

`Dockerfile` stages:
There is no Maven stage: the Dockerfile copies `target/authorization-server.jar` and `target/libs/` from the build context, and `.dockerignore` lets only those two through from `target/`.
1. jlink: builds a custom JRE from `eclipse-temurin:25-jdk-alpine`. Do not use a prebuilt JRE image. The module list comes from `jdeps` over the jar and `libs/*.jar`, plus `jdk.unsupported`.
2. AOT training: runs on plain `alpine` with the jlinked JRE. It starts the jar with `-XX:AOTCacheOutput=app.aot -Ddb.migrate=false` and placeholder `jwt.*` and `admin.*` settings (issuer `https://training.invalid`, a zero session key). It calls `/observe/health`, the API without a valid token, `/admin` (which follows the redirect to `/admin/login` and gets 503, since discovery fails), `/admin` with `HX-Request` (401) and `/admin/static/admin.css`. It then sends SIGTERM, and the JVM writes the cache on exit. BusyBox `wget` has no `--max-redirect`. An unknown option fails silently behind `|| true`, so check new training lines in the build log.
3. Runtime: plain `alpine` with the same JRE, running `-XX:AOTCache=app.aot`.

Base image tags are written directly in each `FROM` line (no `ARG`), so Dependabot can update them.

## CI and dependencies

- `.github/workflows/ci.yml`: on pushes to `main` and on PRs, runs `mvn -B verify` (Testcontainers uses the runner's Docker, no Colima overrides), then builds the image with `docker/build-push-action` using `context: .`. A path context is required: the default git context would not contain `target/`. The image is not pushed.
- `.github/dependabot.yml`: weekly updates for Maven (minor and patch grouped), Docker and GitHub Actions, assigned to `@McKratt`, ignoring Helidon `>= 27` (requires Java 27, non-LTS), with conventional-commit prefixes `build(deps)` / `ci(deps)`. `.github/CODEOWNERS` makes `@McKratt` the required reviewer.

Constraints this imposes:
- The training and runtime stages must use the same JRE, and the jar plus `libs/` must stay at the same paths. Otherwise the JVM silently ignores the cache. Use `-XX:AOTMode=on` to make a mismatch fail instead of being ignored.
- **The server must start without a database**: no DB exists during `docker build`. Hikari uses `initializationFailTimeout=-1`, and Flyway is skipped with `db.migrate=false`. Keep any new startup step tolerant of a missing DB.
- If a new library needs a JDK module that jdeps cannot detect (reflection, `ServiceLoader`), add it next to `jdk.unsupported` in the jlink stage.
- Broaden the training workload (more endpoints hit in the `RUN` heredoc) as features land, so more classes and profiles are cached.

## Local stack (`compose.yaml`)

- `postgres` (`postgres:18-alpine`), `keycloak` (`quay.io/keycloak/keycloak:26.x`, `start-dev --import-realm`, port 8180) and `authorization-server` (`build: .`, port 8080), which starts once the other two are healthy.
- The realm `authz` is imported from `docker/keycloak/authz-realm.json`. It contains:
  - realm roles `authz-admin` / `authz-reader`;
  - client scope `authz-roles` (realm roles as a flat `roles` claim in ID and access tokens);
  - client `authz-admin-ui` (code flow, PKCE S256) and `demo-client` (password grant, audience `authz-server`);
  - users `admin`, `reader`, `alice`.

  Because the file defines `clientScopes`, Keycloak does not create its built-in scopes, so `basic` (sub) and `profile` (`preferred_username`) are defined there too. Without them, tokens have no `preferred_username`.
- Issuer and back channel: `KC_HOSTNAME=http://localhost:8180` with `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`.
  - The issuer is always `http://localhost:8180/realms/authz`.
  - Discovery fetched at `http://keycloak:8080` (`ADMIN_OIDC_DISCOVERY_BASE_URL`) returns token and JWKS endpoints on `keycloak:8080`, which the server container can reach.
  - Authorization and logout endpoints stay on `localhost:8180` for the browser.
- All secrets in `compose.yaml` and the realm file are committed, local-only dev values.
- Helidon maps env vars onto dashed keys (`ADMIN_OIDC_CLIENT_ID` → `admin.oidc.client-id`).

## Purpose

An authorization server with two surfaces:

1. **Authorization API**: a client sends a JWT (identifies the user) plus an application name, and the server answers whether that user is authorized for that application and with which roles.
2. **Admin web UI**: server-rendered pages for administering authorizations, i.e. mapping users to applications and roles.

Core domain: **users**, **applications**, **roles**, and the user ↔ application ↔ role mappings stored in PostgreSQL. The authorization API reads these mappings; the admin UI writes them.

## Tech stack (fixed choices)

- **Java 25**: use modern language features (records, sealed types, pattern matching).
- **Helidon SE** (not Helidon MP): no CDI, no annotations-driven DI or JAX-RS. Routing is wired explicitly in code (`HttpRouting`), and dependencies are passed by constructor from `main`. Helidon 4 SE runs requests on virtual threads, so blocking JDBC calls are fine.
- **HikariCP** connection pool over **PostgreSQL**, with plain JDBC (no ORM).
- **jte** for server-side HTML templates.
- **htmx** for UI interactivity: handlers return HTML fragments rendered by jte, not JSON, for UI requests.
- **Vanilla CSS** only: no CSS frameworks, preprocessors, or a JS/Node build pipeline. htmx is served as a static asset.

## Testing

- **REST Assured** drives HTTP requests against a real, started Helidon server (authorization API and admin UI endpoints), not mocked handlers.
- **WireMock** stands in for the identity provider: it serves the IdP endpoints the server uses to validate JWTs (e.g. the JWKS / OpenID discovery documents). Tests sign their own JWTs with a test key pair whose public key WireMock publishes, so valid, expired, wrongly-signed and wrong-issuer tokens can all be produced on demand.

## Database

Flyway migrations live in `src/main/resources/db/migration` (`V<n>__<description>.sql`) and run at startup from `Main` unless `db.migrate=false`. Nothing is deployed yet, so schema changes go directly into `V1__create_authorization_schema.sql`; add new versioned migrations only after the first deployment, and from then on never edit an applied one. DB settings are under `db.*` in `application.yaml` and can be overridden by env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

Primary keys are `BIGINT` TSIDs generated by the application with `net.bakaar.authz.common.Ids.next()` (hypersistence-tsid), never by the database: always provide `id` in inserts. Run each instance with a distinct `TSID_NODE` env var to rule out collisions. TSIDs exceed JavaScript's safe integer range, so serialize them as strings in JSON and HTML.

Schema: `app_user(username)`, `application(name)`, `role(application_id, name)`, `user_role(user_id, role_id)`. Roles belong to one application, so the same role name in two applications means two different roles. Authorization flow: validated JWT → username claim → `LoadUserRoles.loadRoles(username, application)` → join of the four tables. An unknown user, an unknown application and a user without grants all return an empty set.

## Package structure (hexagonal, per "Get Your Hands Dirty on Clean Architecture")

```
net.bakaar.authz
├── Main                      composition root: builds adapters, services, routing (no DI framework)
├── adapter
│   ├── in/web/api            JSON authorization API (client apps)
│   ├── in/web/admin          admin UI: jte + htmx handlers
│   ├── out/persistence       JDBC/Hikari/PostgreSQL implementations of outgoing ports
│   └── out/idp               JWKS fetch + JWT validation against the IdP
├── application
│   ├── domain/model          users, applications, roles, mappings
│   ├── domain/service        use-case implementations
│   ├── port/in               use-case interfaces + command/query records
│   └── port/out              interfaces the domain needs (persistence, token verification)
└── common                    cross-cutting helpers, no business logic
```

Dependency rules:
- Dependencies point inward: `adapter` → `application.port` → `application.domain`.
- `application` must not import Helidon, JDBC, jte or adapter classes.
- Incoming adapters call only `port.in` interfaces. Domain services implement `port.in` and depend only on `port.out`.
- Outgoing adapters implement `port.out` and map between database rows or tokens and domain models.
- Wiring happens only in `Main`.
- No `Port` / `UseCase` suffix on port interfaces (`LoadUserRoles`, `VerifyAccessToken`, `GetAuthorization`): the package says what they are.

## Authorization API and security invariants

`GET /api/v1/authorizations?application=<name>` with `Authorization: Bearer <jwt>`:
- 200 `{"username","application","roles":[...]}` (roles sorted).
- 400 when `application` is missing, repeated or not `[A-Za-z0-9][A-Za-z0-9._-]{0,99}`.
- 401 with `WWW-Authenticate` when the token is missing, repeated, not Bearer or untrusted. Token problems are reported before parameter problems.
- 404 `{"error":"not_found"}` when the user has no role for the application. Unknown users and unknown applications return the same 404, so they cannot be enumerated.

Invariants (covered by `JwtAccessTokenVerifierTest` and `AuthorizationApiTest`; keep them green):
- `GetAuthorizationQuery` takes the raw `AccessToken`, not a `Username`. The use case verifies the token itself, so no adapter can pass in an arbitrary user. Never add a username parameter or header.
- `JwtAccessTokenVerifier` accepts only:
  - asymmetric algorithms;
  - keys published at the configured `jwt.jwks-uri` (`jku`/`jwk` headers are ignored);
  - the exact issuer, at least one configured audience, a mandatory `exp`, and a non-blank string username claim.
- `jwt.issuer`, `jwt.jwks-uri` and `jwt.audiences` have no defaults. The JWKS URI must be https unless `jwt.allow-insecure-jwks-uri=true` (tests only).
- Responses carry `Cache-Control: no-store`. Unhandled exceptions return a bare 500 and are only logged. `AccessToken.toString()` is redacted; never log the raw token.

## Admin UI and its invariants

The admin UI is routed only when `admin.oidc.issuer` is set; otherwise a WARNING is logged and `/admin` is not routed. Config under `admin.*`:
- `admin.oidc.issuer`, `client-id`, `client-secret`, `redirect-uri` (`<origin>/oidc/redirect`);
- `admin.oidc.discovery-base-url` (optional, defaults to the issuer) and `allow-insecure` (http, local only);
- `admin.session-key` (base64, 32 bytes, required), `admin.session-duration` (default `PT8H`) and `admin.cookie-secure` (default `true`).

Env vars are the upper-case, underscored keys (`ADMIN_SESSION_KEY`, ...).

Sign-in (`LoginRoutes`, `OidcClient`):
- The flow is authorization code with PKCE S256, `state` and `nonce`, using `client_secret_basic`. Login state lives in the encrypted `AUTHZ_ADMIN_LOGIN` cookie (`Path=/oidc`, 10 minutes).
- Discovery is fetched lazily on the first `/admin/login` and cached only on success: the server starts without Keycloak, and an unreachable Keycloak gives 503. The discovered `issuer` must equal `admin.oidc.issuer` exactly.
- The ID token gives the username (`preferred_username`) and the roles (`roles`, a string array). Reads need `authz-admin` or `authz-reader`; writes need `authz-admin`.
- Logout goes through Keycloak's end-session endpoint with `post_logout_redirect_uri=<origin>/admin`.

Invariants (keep them):
- **Session.** It is a stateless encrypted cookie (`AUTHZ_ADMIN_SESSION`, A256GCM, `HttpOnly; SameSite=Lax`, `Secure` unless `admin.cookie-secure=false`). Logout therefore cannot revoke a copied cookie, and role changes in Keycloak apply only at session expiry (8h by default).
- **CSRF.** Every non-GET `/admin` request needs an `X-CSRF-Token` header that matches the session.
- **Headers.** `/admin` and `/oidc` responses carry `Cache-Control: no-store`, and `/admin` responses carry a CSP without inline scripts or styles. The htmx history cache is disabled (`historyCacheSize: 0`, `refreshOnHistoryMiss: true` in `htmx-config`).
- **At least one grant.** Every user always keeps at least one grant.
  - A change that would orphan users deletes them in the same transaction, but only if the request's `confirmedUserIds` equals the set computed in that transaction. Otherwise nothing changes, and the answer is 409 with a new confirmation dialog.
  - This relies on PostgreSQL READ COMMITTED plus `SELECT … FOR UPDATE` on the affected users' rows before the orphans are computed.

`docs/openapi.yaml` is the HTTP contract of both surfaces. Keep it in sync with the routes, and lint it with `npx -y @redocly/cli@1.34.5 lint docs/openapi.yaml`.

## Architecture guidelines

- Keep the authorization API (JSON, machine clients) and the admin UI (HTML, htmx) as separate route groups. They share the domain/persistence layer, not handlers.
- htmx endpoints should support both full-page renders and partial (fragment) renders; detect partial requests through the `HX-Request` header.
