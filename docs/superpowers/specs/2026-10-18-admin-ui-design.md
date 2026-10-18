# Admin UI design

Date: 2026-10-18
Status: draft for review (revised: two tabs)

## Intent

The authorization server answers "which roles does this user have in this application?". Today the
mappings behind that answer can only be changed in the database. The admin UI lets people manage
them in a browser: define applications and their roles, create users, and grant or revoke roles.

Because this UI decides who gets access to what, it is protected by the same kind of identity
provider the API trusts. Admins can change everything. Readers can see everything but change nothing.

A `docker compose` setup starts the whole stack locally (PostgreSQL, Keycloak with a ready-made
realm, the server), so the UI and the API can be tried end to end.

## Goals

- Two tabs: **Users** (home) lists every user with their applications and roles, and
  **Applications** lists every application with its roles.
- Create, edit and delete applications (name, responsible email, description) and their roles
  (name, description).
- Create users by choosing an application first, then one or more of *that application's* roles.
- Grant more roles to an existing user, and revoke single roles.
- Rename and delete users.
- Sign in through Keycloak. The `authz-admin` role may write, `authz-reader` may only read.
- `docker compose up` gives a working stack with test users for both roles.

## Non-goals

- Creating or managing users in Keycloak. Usernames are typed freely and are not checked against
  Keycloak.
- A JSON admin API for machines. The UI's HTTP endpoints return HTML for htmx only.
- Audit log, history or undo.
- Pagination. The data is expected to stay small, and a client-side filter is enough.
- Production deployment of Keycloak (the compose setup is for local use).

## Anti-goals (a failure even if it "works")

- **A user with no grants.** Every user in the database has at least one role, always.
  The server enforces this, not only the UI.
- **Silent removal of users.** Any action that would delete users because they lose their last
  role must first show who will be deleted. The user must confirm, and the server must refuse if
  the list it would delete differs from the list the user confirmed.
- **A reader who can write.** Write endpoints check the role on the server. Hiding buttons is
  only cosmetic.
- **Weakening the authorization API.** The existing API invariants in `CLAUDE.md` stay
  unchanged and their tests stay green.

## Constraints (from the project)

- Helidon SE 4.5.x, Java 25, routing wired in code, wiring only in `Main`. No CDI, no JAX-RS.
- jte templates, htmx (served as a static asset), vanilla CSS. No JS build pipeline, no CSS framework.
- Hexagonal packages as described in `CLAUDE.md`. Port interfaces have no `Port`/`UseCase` suffix.
- Plain JDBC over Hikari. TSID primary keys from `Ids.next()`, serialized as strings in HTML.
- Schema changes go into `V1__create_authorization_schema.sql` (nothing deployed yet).
- **The server must start without a database and without Keycloak** (Docker AOT training stage).
  OIDC discovery must be lazy or tolerant of an unreachable provider at startup.
- htmx endpoints render the full page on normal requests and a fragment when `HX-Request` is present.
- Tests: REST Assured against a started server, Testcontainers PostgreSQL, WireMock as the IdP.

## Decisions

### Domain rules

- **Application**: `name` matches the API's pattern `[A-Za-z0-9][A-Za-z0-9._-]{0,99}`, unique.
  `responsible` is a required email address (simple syntactic check). `description` is optional.
- **Role**: belongs to one application. `name` uses the same pattern, unique within the application.
  `description` optional.
- **User**: `username` is required, trimmed, 1 to 255 characters, unique. It must match the
  `preferred_username` claim the API sees. The admin types it, with no lookup in Keycloak.
- **A user always has at least one grant.**
  - Creating a user requires an application and at least one of its roles, in one transaction.
  - Revoking a user's last role deletes the user.
  - Deleting a role or an application deletes every user left without any role.
  - All three happen in the same transaction as the triggering change.
- **Renames are allowed but visible to clients.** My call: renaming an application or role
  changes what the authorization API matches and returns, so client apps querying the old name
  start getting 404 or different role names. The edit dialog shows this warning; no extra
  confirmation step.
- **Confirmed removal.** A request that would delete users carries the IDs of the users the admin
  confirmed (`confirmedUserIds`). Inside the transaction, the server computes the users that would
  actually be deleted.
  - If the two sets match, it proceeds.
  - If they differ, it rolls back and answers 409 with a fresh confirmation dialog.
  - A change that deletes nobody needs no confirmation.

### Page layout (two tabs)

The approved mockup is `single-page.html` in the brainstorm session, split into two tabs.

- **Tabs**: **Users** (home, `/admin`) and **Applications** (`/admin/applications`).
  - Each tab is a real URL that can be reloaded or bookmarked.
  - Switching tabs is an htmx request that swaps the tab content and pushes the URL
    (`hx-push-url`).
- **Header** (both tabs): the tab bar, plus the signed-in username with a Sign out link.
- **Users tab**:
  - A `+ New user` button (admin only) and a filter box.
  - **Users table**: one row per user and application.
    - Columns: user, application, role chips.
    - Admins see an `×` on each chip and a `+ Grant` button per user.
    - The user name opens a user dialog (rename, delete). The application name opens the
      application dialog.
- **Applications tab**:
  - A `+ New application` button (admin only) and a filter box.
  - **Applications table**: name, responsible, roles, user count. It lists applications nobody
    uses yet too. The name opens the application dialog.
- **New user dialog**: username, then an application `<select>`. Choosing an application loads
  that application's roles as checkboxes. Create is enabled only when at least one role is ticked
  (the server checks again).
- **Grant dialog**: the same dialog with the username fixed.
- **Application dialog** (create and edit): name, responsible, description, plus a role list
  where roles are added, edited and removed. Edit mode adds Delete.
- **Confirmation dialog**: shown before any delete that removes users. It lists those users by name.
- **Filter**: client-side, over the rendered rows. My call: a few lines of inline JS are
  acceptable here, or the builder may make it an htmx request instead.
- **Readers**: the same page without the buttons, inputs and `×` marks. Dialogs open read-only.
- **Dialogs**: native `<dialog>` elements filled by htmx.
- **After each write**: the server returns the updated table of the current tab, and htmx swaps it in.
- **Validation errors**: 422 for bad input and 409 for name conflicts. Both re-render the dialog
  with field messages.

### Visual style (pastel, light and dark)

The approved mockup is `pastel-theme.html` in the brainstorm session.

- **Palette**: soft lavender base (accent `#8b7fd6` in light, `#b3a8f0` in dark), white or
  dark-plum surfaces, rounded cards (12px), pill-shaped chips and soft-tinted buttons.
- **Application colors**: each application gets one pastel from a fixed set (mint, peach, sky,
  rose, butter, plus a few more). It shows as a dot next to the application name and as the
  background of that application's role chips. My call: the color is picked from the
  application ID (for example `id mod palette size`), so it stays stable without a schema change.
- **Dark mode**:
  - Every color is a CSS custom property on `:root`.
  - Dark values apply under `@media (prefers-color-scheme: dark)` and under `[data-theme=dark]`.
  - A ☾/☀ toggle in the header switches the theme and remembers the choice in `localStorage`.
    Without a stored choice, the page follows the system setting.
  - A tiny inline script sets `data-theme` before first paint, so there is no flash.
- **Danger actions** (delete, "remove and delete user") use a soft rose background with darker
  rose text, never the accent color.
- **Contrast**: text and chips meet WCAG AA contrast in both themes.
- All styling stays in one vanilla CSS file served as a static asset.

### HTTP API (HTML/htmx, under `/admin`)

IDs are TSIDs as strings. Form bodies are `application/x-www-form-urlencoded`. Repeated
`roleId` fields carry several roles.

Read (`authz-admin` or `authz-reader`):

| Method | Path | Returns |
|---|---|---|
| GET | `/admin` | Users tab: full page; with `HX-Request`, only the tab content |
| GET | `/admin/applications` | Applications tab: full page; with `HX-Request`, only the tab content |
| GET | `/admin/users/new` | new user dialog |
| GET | `/admin/users/{userId}` | user dialog (rename, delete) |
| GET | `/admin/users/{userId}/grants/new` | grant dialog for that user |
| GET | `/admin/applications/{appId}/role-options` | role checkboxes for the chosen application |
| GET | `/admin/applications/new` | application dialog, empty |
| GET | `/admin/applications/{appId}` | application dialog, filled in |
| GET | `/admin/users/{userId}/grants/{roleId}/delete` | confirmation dialog (or a direct confirm button if nobody is removed) |
| GET | `/admin/users/{userId}/delete` | confirmation dialog |
| GET | `/admin/applications/{appId}/delete` | confirmation dialog listing users that would be removed |
| GET | `/admin/applications/{appId}/roles/{roleId}/delete` | confirmation dialog listing users that would be removed |

Write (`authz-admin` only, otherwise 403):

| Method | Path | Form fields | Effect |
|---|---|---|---|
| POST | `/admin/users` | `username, applicationId, roleId…` | create a user with at least one role of that application |
| PUT | `/admin/users/{userId}` | `username` | rename |
| DELETE | `/admin/users/{userId}` | | delete the user and their grants |
| POST | `/admin/users/{userId}/grants` | `applicationId, roleId…` | add roles (already-held roles are ignored) |
| DELETE | `/admin/users/{userId}/grants/{roleId}` | `confirmedUserIds…` | revoke; deletes the user if it was the last role |
| POST | `/admin/applications` | `name, responsible, description, roleName…, roleDescription…` | create an application with its initial roles (zero roles allowed) |
| PUT | `/admin/applications/{appId}` | `name, responsible, description` | update |
| DELETE | `/admin/applications/{appId}` | `confirmedUserIds…` | delete with its roles and grants; deletes users left without roles |
| POST | `/admin/applications/{appId}/roles` | `name, description` | add a role |
| PUT | `/admin/applications/{appId}/roles/{roleId}` | `name, description` | update a role |
| DELETE | `/admin/applications/{appId}/roles/{roleId}` | `confirmedUserIds…` | delete; deletes users left without roles |

Status codes:
- 200 with fragments on success.
- 404 for unknown IDs.
- 409 for a duplicate name or a stale confirmation.
- 422 for invalid input, or roles that do not belong to the chosen application.
- 403 for readers.
- Unauthenticated requests redirect to Keycloak (htmx requests get 401 plus `HX-Redirect`, so
  the browser does a full redirect).

Every response carries `Cache-Control: no-store`. Unhandled exceptions stay a bare 500.

### Sign-in and security

- **Flow**: OIDC authorization code flow, run by the server as a confidential Keycloak client
  (`authz-admin-ui`). The session lives in a cookie.
- **Library**: my call is Helidon's OIDC security provider
  (`helidon-security-providers-oidc` + `helidon-webserver-security`), which provides the code
  flow, the redirect endpoint and an encrypted cookie. If it turns out to fight the "start
  without Keycloak" constraint, the builder may write a small code-flow adapter in
  `adapter/in/web/admin` on top of Nimbus instead (Nimbus is already a dependency).
- **Endpoints**:
  - `/oidc/redirect` is the callback.
  - `/admin/logout` clears the session and goes to Keycloak's end-session endpoint.
- **Roles**: Keycloak realm roles `authz-admin` and `authz-reader`, mapped into a flat `roles`
  claim. A signed-in user with neither role gets 403.
- **Cookie**: `HttpOnly; Secure` (except on localhost in compose); `SameSite=Lax` so the
  redirect back from Keycloak keeps the session.
- **CSRF**:
  - A per-session token is rendered in the page and sent by htmx as an `X-CSRF-Token` header
    (`hx-headers` on `<body>`).
  - Every non-GET request without a matching token gets 403.
  - My call, since `Lax` alone does not cover every case.
- **Configuration**: admin UI settings live under a new `admin.oidc.*` key: issuer, client id,
  client secret, redirect URI, and an optional internal base URL for back-channel calls.
- **The authorization API** keeps its own `jwt.*` bearer validation, unchanged.

### Backend structure

- **`port.in`**: one interface per action, with commands as records:
  - `ListAccessOverview` (read model for the page), `GetApplication`, `ListRolesOfApplication`
  - `CreateUser`, `RenameUser`, `DeleteUser`, `GrantRoles`, `RevokeRole`
  - `CreateApplication`, `UpdateApplication`, `DeleteApplication`, `AddRole`, `UpdateRole`, `DeleteRole`
  - `PreviewUserRemoval`, which returns the users a given delete would remove (feeds the
    confirmation dialogs)
- **`domain.service`**: implements these ports, validates commands, and enforces the
  "at least one grant" and confirmed-removal rules.
- **`port.out`**: persistence interfaces, plus a transaction boundary the service can use without
  importing JDBC. The builder picks its shape, for example a `Transactions` port that runs a
  lambda in one connection.
- **`adapter/out/persistence`**: JDBC implementations, mapping unique-constraint violations to
  domain conflicts.
- **`adapter/in/web/admin`**: Helidon handlers, jte templates, and the role checks.

### Schema change (edit V1)

- Add `responsible VARCHAR(254) NOT NULL` to `application`.
- The existing `ON DELETE CASCADE` constraints stay. Removing roleless users is done explicitly
  by the service in the same transaction, never by a trigger.

### docker compose

`compose.yaml` at the repository root starts three services.

- **postgres** (`postgres:18-alpine`): database `authz`.
- **keycloak** (official image, pinned tag):
  - Runs `start-dev --import-realm`, reading the realm from `docker/keycloak/authz-realm.json`.
  - Published on `localhost:8180`.
  - `KC_HOSTNAME=http://localhost:8180` plus a back-channel setting, so tokens issued in the
    browser carry an issuer the server accepts even though it calls Keycloak at
    `http://keycloak:8080`.
- **authorization-server**:
  - Built from the existing `Dockerfile` (after `mvn package`), on `localhost:8080`.
  - Configured through env vars, with `jwt.allow-insecure-jwks-uri=true` (local only).

The realm (`authz`) contains:
- **Clients**:
  - `authz-admin-ui`: confidential, code flow, redirect `http://localhost:8080/oidc/redirect`.
  - `demo-client`: for calling the API, with an audience mapper so its tokens are accepted by
    `jwt.audiences`.
- **Realm roles**: `authz-admin` and `authz-reader`, with a mapper to the `roles` claim.
- **Users**: `admin` (authz-admin), `reader` (authz-reader), and `alice` (no admin role, to be
  granted roles in the UI and then call the API). Passwords are throwaway values kept in the
  realm file and listed in the README.

The README gets a short "run locally" section: `mvn package`, `docker compose up`, sign in as
admin, create an application and grant alice, fetch a token for alice, call the API.

### Testing

- **Admin endpoints**: REST Assured tests against a started server.
  - Full page vs fragment.
  - Role enforcement (reader gets 403 on every write, anonymous is redirected).
  - CSRF rejection.
  - The "at least one grant" rule on every path: create without a role, revoke the last role,
    delete a role, delete an application.
  - Stale-confirmation 409.
  - Validation (422) and duplicates (409).
- **Sign-in**: WireMock plays Keycloak (discovery, JWKS, token endpoint). The builder decides
  whether tests drive the code flow or inject a session.
- **Persistence**: Testcontainers tests for the new adapters.
- **Training**: the Docker AOT training workload also hits `/admin` (expecting a redirect).

## Left to the builder

- Exact jte template split and CSS.
- Transaction port shape.
- Whether the filter is inline JS or htmx.
- How tests obtain an authenticated session.
- The exact Keycloak image tag and the realm JSON details.
