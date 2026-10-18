package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.Username;

public record CreateUserCommand(Username username, ApplicationId application, Set<RoleId> roles) {
}
