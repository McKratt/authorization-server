package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UnconfirmedRemovalException;
import net.bakaar.authz.domain.UserId;

public interface RoleDeletion {

    /** @throws UnconfirmedRemovalException when {@code confirmedRemovals} differs from the users that would be removed */
    void deleteRole(ApplicationId app, RoleId role, Set<UserId> confirmedRemovals);
}
