package net.bakaar.authz.adapter.in.web.admin.auth;

import java.util.Arrays;
import java.util.Optional;

/**
 * Keycloak realm roles that open the admin UI. {@link #ADMIN} may write, {@link #READER} may only read.
 */
public enum AdminRole {
    ADMIN("authz-admin"),
    READER("authz-reader");

    private final String claimValue;

    AdminRole(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    /**
     * The admin role for a value of the {@code roles} claim; other roles are not admin roles.
     */
    public static Optional<AdminRole> fromClaim(String value) {
        return Arrays.stream(values()).filter(role -> role.claimValue.equals(value)).findFirst();
    }
}
