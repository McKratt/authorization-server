package net.bakaar.authz.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.domain.ApplicationDetails;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.ApplicationSummary;
import net.bakaar.authz.domain.Description;
import net.bakaar.authz.domain.Email;
import net.bakaar.authz.domain.NewRole;
import net.bakaar.authz.domain.RoleId;

public interface ApplicationStore {

    void insert(ApplicationId id, ApplicationName name, Email responsible, Description description);

    boolean update(ApplicationId id, ApplicationName name, Email responsible, Description description);

    boolean delete(ApplicationId id);

    Optional<ApplicationDetails> find(ApplicationId id);

    /** Sorted by name. */
    List<ApplicationSummary> findAll();

    void insertRole(ApplicationId app, RoleId id, NewRole role);

    boolean updateRole(ApplicationId app, RoleId id, NewRole role);

    boolean deleteRole(ApplicationId app, RoleId id);

    Set<RoleId> roleIds(ApplicationId app);

    /*
     * Locks for the at-least-one-grant invariant. Every lock lasts until the transaction ends. Writers that add
     * grants or roles lock what they reference against deletion; deleters lock what they delete for deletion, so
     * the two wait for each other and the deleter computes orphans from committed grants only.
     * Order: application, then roles, then users (GrantStore, UserStore).
     */

    /** Shared lock: the application cannot be deleted meanwhile. Returns whether it exists. */
    boolean lockAgainstDeletion(ApplicationId id);

    /** Exclusive lock before deleting the application; waits for writers holding a shared lock. Returns whether it exists. */
    boolean lockForDeletion(ApplicationId id);

    /**
     * Shared locks, in id order, on those of {@code roles} that belong to {@code app}: they cannot be deleted
     * meanwhile. Returns the roles found; a role missing from the result is unknown, of another application, or
     * was deleted by a transaction this one waited for.
     */
    Set<RoleId> lockRolesAgainstDeletion(ApplicationId app, Set<RoleId> roles);

    /** Exclusive lock before deleting the role; waits for writers holding a shared lock. Returns whether it belongs to {@code app}. */
    boolean lockRoleForDeletion(ApplicationId app, RoleId role);

    /** Exclusive locks, in id order, on every role of {@code app}; returns their ids. */
    Set<RoleId> lockRolesForDeletion(ApplicationId app);
}
