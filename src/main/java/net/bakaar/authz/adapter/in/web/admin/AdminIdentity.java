package net.bakaar.authz.adapter.in.web.admin.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Who signed in, taken from a verified ID token.
 *
 * @param idToken the raw ID token, kept for the logout hint; never printed
 */
public record AdminIdentity(String username, Set<AdminRole> roles, String idToken) {

    public AdminIdentity {
        Objects.requireNonNull(username, "username");
        roles = Set.copyOf(roles);
        Objects.requireNonNull(idToken, "idToken");
    }

    @Override
    public String toString() {
        return "AdminIdentity[username=" + username + ", roles=" + roles + "]";
    }
}
