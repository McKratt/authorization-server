package net.bakaar.authz.application.port.in;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.InvalidInputException;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserId;

public record GrantRolesCommand(UserId user, ApplicationId application, Set<RoleId> roles) {

    public GrantRolesCommand {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(application, "application must not be null");
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new InvalidInputException(Map.of("roleId", "Select at least one role"));
        }
    }
}
