package net.bakaar.authz.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;

public interface GrantStore {

    /** Idempotent: already held roles are ignored. */
    void grant(UserId user, Set<RoleId> roles);

    boolean revoke(UserId user, RoleId role);

    /** Users (optionally only one) whose every role is among {@code removed}, i.e. who would be left without grant. */
    List<UserSummary> usersLosingAllRoles(Set<RoleId> removed, Optional<UserId> onlyUser);

    /**
     * Locks, in id order, every user holding one of {@code roles}, so a concurrent change to their grants
     * waits until this transaction ends. Call before {@link #usersLosingAllRoles}.
     */
    void lockUsersHolding(Set<RoleId> roles);
}
