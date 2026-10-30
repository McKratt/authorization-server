package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.NewRole;
import net.bakaar.authz.domain.RoleId;

public interface RoleAddition {

    RoleId addRole(ApplicationId app, NewRole role);
}
