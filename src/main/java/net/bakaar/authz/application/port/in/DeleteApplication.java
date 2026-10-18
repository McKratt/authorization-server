package net.bakaar.authz.application.port.in;

import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserId;

public interface DeleteApplication {

    /** @throws UnconfirmedRemovalException when {@code confirmedRemovals} differs from the users that would be removed */
    void deleteApplication(ApplicationId id, Set<UserId> confirmedRemovals);
}
