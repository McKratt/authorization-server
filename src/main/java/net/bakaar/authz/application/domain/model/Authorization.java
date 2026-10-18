package net.bakaar.authz.application.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * The roles a user holds for one application. Always contains at least one role.
 */
public record Authorization(Username username, ApplicationName application, Set<Role> roles) {

    public Authorization {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(application, "application must not be null");
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("an authorization needs at least one role");
        }
    }
}
