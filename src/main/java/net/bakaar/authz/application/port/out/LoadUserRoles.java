package net.bakaar.authz.application.port.out;

import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Role;
import net.bakaar.authz.application.domain.model.Username;

/**
 * Loads the roles a user holds for one application.
 */
public interface LoadUserRoles {

    /**
     * @return the user's roles for the application; empty when the user, the application or any grant is unknown
     */
    Set<Role> loadRoles(Username username, ApplicationName application);
}
