package net.bakaar.authz.application.port.in;

import java.util.Objects;
import java.util.Set;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserId;

public record RevokeRoleCommand(UserId user, RoleId role, Set<UserId> confirmedRemovals) {

    public RevokeRoleCommand {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(role, "role must not be null");
        confirmedRemovals = Set.copyOf(confirmedRemovals);
    }
}
