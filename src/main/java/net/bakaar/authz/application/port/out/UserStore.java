package net.bakaar.authz.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.bakaar.authz.domain.UserAccess;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.UserSummary;
import net.bakaar.authz.domain.Username;

public interface UserStore {

    void insert(UserId id, Username username);

    boolean rename(UserId id, Username username);

    boolean delete(UserId id);

    /** Locks the user row until the transaction ends; returns whether the user exists. */
    boolean lock(UserId id);

    Optional<UserAccess> find(UserId id);

    /** Sorted by username. */
    List<UserAccess> findAll();

    /** Deletes the candidates that still hold no role; returns those actually deleted. */
    List<UserSummary> deleteUsersWithoutRoles(Collection<UserId> candidates);
}
