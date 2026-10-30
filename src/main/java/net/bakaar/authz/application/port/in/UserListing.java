package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.domain.UserAccess;

public interface UserListing {

    /** Sorted by username. */
    List<UserAccess> listUsers();
}
