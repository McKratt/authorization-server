package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.UnconfirmedRemovalException;

public interface RoleRevocation {

    /** @throws UnconfirmedRemovalException when revoking the user's last role and the user is not confirmed for removal */
    void revokeRole(RevokeRoleCommand command);
}
