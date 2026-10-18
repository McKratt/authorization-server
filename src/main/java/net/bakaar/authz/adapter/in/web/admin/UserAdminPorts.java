package net.bakaar.authz.adapter.in.web.admin;

import net.bakaar.authz.application.port.in.CreateUser;
import net.bakaar.authz.application.port.in.DeleteUser;
import net.bakaar.authz.application.port.in.GetUser;
import net.bakaar.authz.application.port.in.GrantRoles;
import net.bakaar.authz.application.port.in.ListUsers;
import net.bakaar.authz.application.port.in.PreviewUserChanges;
import net.bakaar.authz.application.port.in.RenameUser;
import net.bakaar.authz.application.port.in.RevokeRole;

/**
 * The user use cases the admin UI calls, built in {@code Main}.
 */
public record UserAdminPorts(ListUsers list, GetUser get, CreateUser create, RenameUser rename, DeleteUser delete,
                             GrantRoles grantRoles, RevokeRole revokeRole, PreviewUserChanges preview) {
}
