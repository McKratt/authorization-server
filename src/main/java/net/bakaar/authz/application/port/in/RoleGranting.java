package net.bakaar.authz.application.port.in;

public interface GrantRoles {

    /** Roles already held are ignored. */
    void grantRoles(GrantRolesCommand command);
}
