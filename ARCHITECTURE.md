# Architecture

The code follows the `mk-helidon:hexagonal-helidon` skill: ports and adapters in the package layout of the first
edition of *Get Your Hands Dirty on Clean Architecture* (Tom Hombergs), with application services distinct from the
domain as in *Implementing Domain-Driven Design* (Vaughn Vernon). There is one bounded context, so the hexagon sits
directly in `net.bakaar.authz`. `CLAUDE.md` describes the package tree and the rules; `ArchitectureTest` enforces
them.

This file records where the code deliberately departs from the skill, and why.

## JDBC and HikariCP instead of Helidon DbClient

The skill's default persistence is Helidon DbClient with a `DbClientTransactionRunner`. This project keeps plain JDBC
over HikariCP (`JdbcTransactionRunner` binds one connection to the request's virtual thread).

Why: the at-least-one-grant invariant relies on PostgreSQL row locks taken in a fixed order (application → roles →
users), with `FOR UPDATE` for deleters and `FOR KEY SHARE` for writers. The SQL and its concurrency tests
(`UserAdminServiceIT`, `ApplicationAdminServiceIT`) are proven on JDBC. Porting them to DbClient would add risk and
gain nothing. The transaction boundary still goes through the outgoing port `TransactionRunner`, so the application
services do not depend on the choice.

## Application services implement several narrow ports

Every use case has its own incoming port (`UserCreation`, `RoleRevocation`, ...). The three application services
each implement a group of them (`UserAdminService`, `ApplicationAdminService`, `AuthorizationService`), instead of
one service class per use case.

Why: the use cases of a group share the same stores and private helpers (lock order, orphan computation, not-found
errors). Splitting them would duplicate those helpers or move them into a shared class with no meaning of its own. The
incoming adapters still see only the narrow ports.

## Public jte template models

Adapter classes are package-private, and each adapter package exposes only its `*AdapterConfiguration`. The exception
is the four types used by the admin templates: `AdminSession`, `RoleInput`, `ApplicationColors` and `FilterTexts`.

Why: `jte-maven-plugin` generates the template classes in `gg.jte.generated.precompiled...`, a different package, so
everything a template references must be public. `ArchitectureTest.TEMPLATE_MODELS` lists them. Keep new template
models in that list, or pass plain values to the templates instead.

## Port bundles nested in the admin adapter configuration

`AuthorizationAdminAdapterConfiguration.UserAdminPorts` and `ApplicationAdminPorts` are public records nested in the
configuration, so `configuration` can hand the 17 admin use cases to the admin adapter in two values.

## A domain made mostly of value objects

The domain holds immutable value objects (`Username`, `RoleName`, `Email`, ids...), read models (`UserAccess`,
`ApplicationDetails`...) and the rule `RemovalConfirmation` (a change may only delete the users it orphans when the
caller confirmed exactly those users). There are no entities that carry their own state changes.

Why: the main invariant ("every user keeps at least one grant") spans users, roles and applications that other
transactions change concurrently. It is enforced inside one database transaction with row locks, which an in-memory
aggregate cannot do. Loading a whole application graph to change one grant would not make it safer. The application
services orchestrate the locks; the decision itself lives in the domain.

## Outgoing HTTP of the identity provider adapter

The skill requires Helidon `WebClient` for outgoing HTTP. `OidcClient` (admin sign-in) uses it. Nimbus JOSE fetches
the JWKS through its own `ResourceRetriever` in `JwtAccessTokenVerifier` and `OidcClient`, with explicit timeouts:
that fetch happens inside the library, and its key caching and rotation handling are part of what the security
invariants rely on.

## Sign-in to Keycloak from the admin web adapter

`OidcClient` calls Keycloak, another application, from the incoming adapter `adapter/in/web/admin`, not from an
outgoing adapter behind a port. Sign-in and the session cookie authenticate the web request itself. They are not a
dependency of any use case, so no port of the application has a reason to know them.

## Use case tests start the server themselves

The `*IT` tests start the real wiring with `AuthorizationServerConfiguration.start(config)` in `@BeforeAll` and drive
it with REST Assured. They do not use Helidon's `@ServerTest` / `@SetUpRoute`.

Why: the project requires REST Assured, and `start` is the code path `main` runs. That path includes the validation
of the settings before the pool exists, the Flyway migration, and closing the pool on a failed start.

## Not yet done

Some write use cases still take loose parameters instead of a self-validating command (`ApplicationDeletion`,
`RoleDeletion`, `RoleAddition`, `RoleUpdate`, `UserRenaming`). Their values are domain value objects that validate
themselves. Give them a command when they get more inputs.
