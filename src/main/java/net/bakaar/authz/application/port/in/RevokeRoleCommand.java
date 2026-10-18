package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;

public record RevokeRoleCommand(UserId user, RoleId role, Set<UserId> confirmedRemovals) {
}
