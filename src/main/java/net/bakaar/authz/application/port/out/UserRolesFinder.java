package net.bakaar.authz.application.port.out;

import java.util.Set;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Role;
import net.bakaar.authz.domain.Username;

/**
 * Loads the roles a user holds for one application.
 */
public interface UserRolesFinder {

    /**
     * @return the user's roles for the application; empty when the user, the application or any grant is unknown
     */
    Set<Role> loadRoles(Username username, ApplicationName application);
}
