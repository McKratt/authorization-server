package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.UnconfirmedRemovalException;
import net.bakaar.authz.domain.UserId;

public interface ApplicationDeletion {

    /** @throws UnconfirmedRemovalException when {@code confirmedRemovals} differs from the users that would be removed */
    void deleteApplication(ApplicationId id, Set<UserId> confirmedRemovals);
}
