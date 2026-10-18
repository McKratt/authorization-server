# Authorization server

Answers whether a user, identified by a JWT, is authorized for an application, and with which roles. A
Keycloak-protected admin web UI maps users to applications and roles. The data is stored in PostgreSQL.

- `GET /api/v1/authorizations?application=<name>` with `Authorization: Bearer <jwt>`: JSON API for client
  applications.
- `/admin`: server-rendered admin UI (jte + htmx). Users with the realm role `authz-admin` can read and write;
  users with `authz-reader` can only read.

The HTTP contract is in [`docs/openapi.yaml`](docs/openapi.yaml).

## Run locally

Requires Java 25, Maven and Docker.

```sh
mvn package
docker compose up --build
```

[`compose.yaml`](compose.yaml) starts three services:

| Service                | URL                     | Notes                                                       |
|------------------------|-------------------------|-------------------------------------------------------------|
| `authorization-server` | <http://localhost:8080> | API and admin UI                                            |
| `keycloak`             | <http://localhost:8180> | realm `authz` imported from `docker/keycloak/authz-realm.json`; console login `admin` / `admin` |
| `postgres`             | not published           | database `authz`, user `authz` / `authz`                    |

> **Local use only.** Every password, client secret and the session key in `compose.yaml` and
> `docker/keycloak/authz-realm.json` is a throwaway development value committed in this repository. Never
> reuse them for a real deployment: generate a session key with `openssl rand -base64 32`, and create the
> clients, secrets and users in your own Keycloak.

Users of the `authz` realm:

| User     | Password              | Realm role     | Can do                                    |
|----------|-----------------------|----------------|-------------------------------------------|
| `admin`  | `admin-dev-password`  | `authz-admin`  | everything in the admin UI                |
| `reader` | `reader-dev-password` | `authz-reader` | read the admin UI                         |
| `alice`  | `alice-dev-password`  | none           | no admin UI; a sample user for the API    |

Clients of the `authz` realm:

| Client           | Secret                           | Use                                                                     |
|------------------|----------------------------------|-------------------------------------------------------------------------|
| `authz-admin-ui` | `dev-only-authz-admin-ui-secret` | sign-in to the admin UI (authorization code flow with PKCE S256)        |
| `demo-client`    | `dev-only-demo-client-secret`    | a sample client app: password grant, access tokens with audience `authz-server` |

### Try it

1. Open <http://localhost:8080/admin> and sign in as `admin` / `admin-dev-password`.
2. In the **Applications** tab, create an application `wiki` with a role `editor`.
3. In the **Users** tab, create the user `alice` with the grant `wiki` / `editor`.
4. Get an access token for alice from `demo-client` (password grant):

   ```sh
   TOKEN=$(curl -s \
       -d grant_type=password -d client_id=demo-client -d client_secret=dev-only-demo-client-secret \
       -d username=alice -d password=alice-dev-password \
       http://localhost:8180/realms/authz/protocol/openid-connect/token | jq -r .access_token)
   ```

5. Ask the authorization API for alice's roles in `wiki`:

   ```sh
   curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/authorizations?application=wiki'
   # {"username":"alice","application":"wiki","roles":["editor"]}
   ```

   An application alice has no role for answers `404 {"error":"not_found"}`.

Sign in as `reader` / `reader-dev-password` to see the read-only admin UI.

Stop with `docker compose down`, or `docker compose down -v` to also drop the database.

## Configuration

Settings are in [`src/main/resources/application.yaml`](src/main/resources/application.yaml) and can be
overridden by environment variables (`db.url` → `DB_URL`, `admin.oidc.client-id` → `ADMIN_OIDC_CLIENT_ID`, ...).

| Key                             | Env var                         | Notes                                                                |
|---------------------------------|---------------------------------|----------------------------------------------------------------------|
| `db.url`, `db.username`, `db.password` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection                                  |
| `jwt.issuer`                    | `JWT_ISSUER`                    | required: exact `iss` of the API's access tokens                     |
| `jwt.jwks-uri`                  | `JWT_JWKS_URI`                  | required: https unless `jwt.allow-insecure-jwks-uri=true`            |
| `jwt.audiences`                 | `JWT_AUDIENCES`                 | required: comma-separated, at least one must be in `aud`             |
| `admin.oidc.issuer`             | `ADMIN_OIDC_ISSUER`             | enables the admin UI; without it `/admin` is not routed              |
| `admin.oidc.discovery-base-url` | `ADMIN_OIDC_DISCOVERY_BASE_URL` | optional: internal address of Keycloak, defaults to the issuer       |
| `admin.oidc.client-id`, `admin.oidc.client-secret` | `ADMIN_OIDC_CLIENT_ID`, `ADMIN_OIDC_CLIENT_SECRET` | confidential Keycloak client |
| `admin.oidc.redirect-uri`       | `ADMIN_OIDC_REDIRECT_URI`       | `<origin>/oidc/redirect`, registered at Keycloak                     |
| `admin.oidc.allow-insecure`     | `ADMIN_OIDC_ALLOW_INSECURE`     | local only: allows http to Keycloak                                  |
| `admin.session-key`             | `ADMIN_SESSION_KEY`             | required with the admin UI: base64 of 32 random bytes                |
| `admin.session-duration`        | `ADMIN_SESSION_DURATION`        | default `PT8H`                                                       |
| `admin.cookie-secure`           | `ADMIN_COOKIE_SECURE`           | default `true`; `false` only for plain-http local runs               |
| (system property `tsid.node`)   | `TSID_NODE`                     | TSID node id, distinct per instance (read by hypersistence-tsid)     |

## Build and test

```sh
mvn package                         # compile, test, build target/authorization-server.jar + target/libs/
docker build -t authorization-server .   # image with a jlinked JRE and a Leyden AOT cache
```

The tests start PostgreSQL through Testcontainers, so Docker must be running.
