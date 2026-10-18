package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.application.domain.model.UserAccess;

public interface ListUsers {

    /** Sorted by username. */
    List<UserAccess> listUsers();
}
