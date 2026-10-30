package net.bakaar.authz.application.port.in;

public interface RoleGranting {

    /** Roles already held are ignored. */
    void grantRoles(GrantRolesCommand command);
}
