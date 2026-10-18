# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Maven, with `io.helidon.applications:helidon-se` (4.5.x) as parent. Helidon 27+ requires Java 27, so stay on 4.x while on Java 25.

```sh
mvn package                                   # compile, test, build target/authorization-server.jar + target/libs/
mvn test -Dtest=AuthorizationApiTest          # single test class
mvn test -Dtest=AuthorizationApiTest#returns401WithoutToken   # single test method
java -jar target/authorization-server.jar     # run (port 8080, config in src/main/resources/application.yaml)
docker build -t authorization-server .        # container image
```

Persistence tests start `postgres:18-alpine` through Testcontainers 2.x, so Docker must be running. With Colima (the local setup), Testcontainers needs these two variables, otherwise it fails with `Could not connect to Ryuk at null:...`:

```sh
export TESTCONTAINERS_HOST_OVERRIDE=localhost
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Base package: `net.bakaar` (application code under `net.bakaar.authz`). Entry point: `net.bakaar.authz.Main`; `Main.start(Config)` is what tests call to boot a server (use `server.port=0`).

## Container image and Leyden AOT

`Dockerfile` stages:
1. Maven build.
2. jlink: builds a custom JRE from `eclipse-temurin:25-jdk-alpine`. Do not use a prebuilt JRE image. The module list comes from `jdeps` over the jar and `libs/*.jar`, plus `jdk.unsupported`.
3. AOT training: runs on plain `alpine` with the jlinked JRE. It starts the jar with `-XX:AOTCacheOutput=app.aot -Ddb.migrate=false`, calls `/observe/health`, then sends SIGTERM; the JVM writes the cache on exit.
4. Runtime: plain `alpine` with the same JRE, running `-XX:AOTCache=app.aot`.

Constraints this imposes:
- The training and runtime stages must use the same JRE, and the jar plus `libs/` must stay at the same paths. Otherwise the JVM silently ignores the cache. Use `-XX:AOTMode=on` to make a mismatch fail instead of being ignored.
- **The server must start without a database**: no DB exists during `docker build`. Hikari uses `initializationFailTimeout=-1`, and Flyway is skipped with `db.migrate=false`. Keep any new startup step tolerant of a missing DB.
- If a new library needs a JDK module that jdeps cannot detect (reflection, `ServiceLoader`), add it next to `jdk.unsupported` in the jlink stage.
- Broaden the training workload (more endpoints hit in the `RUN` heredoc) as features land, so more classes and profiles are cached.

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

Flyway migrations live in `src/main/resources/db/migration` (`V<n>__<description>.sql`) and run at startup from `Main` unless `db.migrate=false`. Never edit an applied migration; add a new one. DB settings are under `db.*` in `application.yaml` and can be overridden by env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

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

## Architecture guidelines

- Keep the authorization API (JSON, machine clients) and the admin UI (HTML, htmx) as separate route groups. They share the domain/persistence layer, not handlers.
- htmx endpoints should support both full-page renders and partial (fragment) renders; detect partial requests through the `HX-Request` header.
