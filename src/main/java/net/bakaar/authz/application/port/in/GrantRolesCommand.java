package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;

public record GrantRolesCommand(UserId user, ApplicationId application, Set<RoleId> roles) {
}
