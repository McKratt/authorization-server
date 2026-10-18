package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.RoleId;

public interface AddRole {

    RoleId addRole(ApplicationId app, NewRole role);
}
