package net.bakaar.authz.adapter.in.web.admin.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Who signed in, taken from a verified ID token.
 */
public record AdminIdentity(String username, Set<AdminRole> roles) {

    public AdminIdentity {
        Objects.requireNonNull(username, "username");
        roles = Set.copyOf(roles);
    }
}
