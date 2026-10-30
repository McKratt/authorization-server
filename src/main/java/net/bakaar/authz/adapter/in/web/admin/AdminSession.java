package net.bakaar.authz.adapter.in.web.admin.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A signed-in admin UI user, carried in the encrypted {@code AUTHZ_ADMIN_SESSION} cookie.
 *
 * @param username  {@code preferred_username} of the ID token
 * @param roles     admin roles held at sign-in; empty means the UI refuses every request
 * @param csrfToken per-session token every non-GET request must echo in {@code X-CSRF-Token}
 * @param idToken   the raw ID token of the sign-in, sent back to Keycloak as {@code id_token_hint} on logout so it
 *                  ends the SSO session without asking; never printed
 * @param expiresAt end of the session
 */
public record AdminSession(String username, Set<AdminRole> roles, String csrfToken, String idToken,
                           Instant expiresAt) {

    /**
     * Base64url without padding: pages embed the token unescaped-safe in a JSON attribute ({@code hx-headers}).
     */
    private static final Pattern CSRF_TOKEN = Pattern.compile("[A-Za-z0-9_-]+");

    public AdminSession {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(csrfToken, "csrfToken");
        if (!CSRF_TOKEN.matcher(csrfToken).matches()) {
            throw new IllegalArgumentException("csrfToken must be base64url");
        }
        Objects.requireNonNull(idToken, "idToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
        roles = Set.copyOf(roles);
    }

    public boolean canRead() {
        return !roles.isEmpty();
    }

    public boolean canWrite() {
        return roles.contains(AdminRole.ADMIN);
    }

    @Override
    public String toString() {
        return "AdminSession[username=" + username + ", roles=" + roles + ", expiresAt=" + expiresAt + "]";
    }
}
