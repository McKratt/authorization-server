package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.RoleId;

public interface UpdateRole {

    void updateRole(ApplicationId app, RoleId role, NewRole update);
}
