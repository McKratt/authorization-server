package net.bakaar.authz.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.bakaar.authz.domain.InvalidInputException;
import net.bakaar.authz.domain.NotFoundException;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.RemovalConfirmation;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserAccess;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.UserSummary;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.application.port.in.UserCreation;
import net.bakaar.authz.application.port.in.CreateUserCommand;
import net.bakaar.authz.application.port.in.UserDeletion;
import net.bakaar.authz.application.port.in.UserLookup;
import net.bakaar.authz.application.port.in.RoleGranting;
import net.bakaar.authz.application.port.in.GrantRolesCommand;
import net.bakaar.authz.application.port.in.UserListing;
import net.bakaar.authz.application.port.in.UserChangePreview;
import net.bakaar.authz.application.port.in.UserRenaming;
import net.bakaar.authz.application.port.in.RoleRevocation;
import net.bakaar.authz.application.port.in.RevokeRoleCommand;
import net.bakaar.authz.application.port.out.ApplicationStore;
import net.bakaar.authz.application.port.out.GrantStore;
import net.bakaar.authz.application.port.out.TransactionRunner;
import net.bakaar.authz.application.port.out.UserStore;

/**
 * Administers users and their grants. Every use case, reads included, runs in one transaction.
 * A user always holds at least one role: revoking the last one deletes the user, but only when the
 * caller confirmed exactly the user computed inside the same transaction.
 * <p>
 * Granting locks the granted roles against deletion before inserting, so a concurrent role or application deletion
 * either waits and then sees the new grant (and asks to confirm the orphans), or wins and the grant answers 422.
 * Locks are taken roles first, then the user, like the deletions do.
 */
@RequiredArgsConstructor
public class UserAdminService implements UserListing, UserLookup, UserCreation, UserRenaming, UserDeletion, RoleGranting,
        RoleRevocation, UserChangePreview {

    private final TransactionRunner transactions;
    private final UserStore users;
    private final ApplicationStore applications;
    private final GrantStore grants;

    @Override
    public List<UserAccess> listUsers() {
        return transactions.inTransaction(users::findAll);
    }

    @Override
    public UserAccess getUser(UserId id) {
        return transactions.inTransaction(() -> requireUser(id));
    }

    @Override
    public UserId createUser(CreateUserCommand command) {
        return transactions.inTransaction(() -> {
            lockRolesOfApplication(command.application(), command.roles());
            UserId id = UserId.next();
            users.insert(id, command.username());
            grants.grant(id, command.roles());
            return id;
        });
    }

    @Override
    public void renameUser(UserId id, Username username) {
        transactions.run(() -> {
            if (!users.rename(id, username)) {
                throw userNotFound(id);
            }
        });
    }

    @Override
    public void deleteUser(UserId id) {
        transactions.run(() -> {
            if (!users.delete(id)) {
                throw userNotFound(id);
            }
        });
    }

    @Override
    public void grantRoles(GrantRolesCommand command) {
        transactions.run(() -> {
            lockRolesOfApplication(command.application(), command.roles());
            // The row lock also makes a concurrent orphan deletion of this user wait for the grant.
            if (!users.lock(command.user())) {
                throw userNotFound(command.user());
            }
            grants.grant(command.user(), command.roles());
        });
    }

    @Override
    public void revokeRole(RevokeRoleCommand command) {
        transactions.run(() -> {
            // Locking the user first serializes with application/role deletes and other revokes on this user.
            requireHeldRole(command.user(), command.role());
            Set<UserId> removed = new RemovalConfirmation(orphansOf(command.user(), command.role()),
                    command.confirmedRemovals()).requireConfirmed();
            grants.revoke(command.user(), command.role());
            users.deleteUsersWithoutRoles(removed);
        });
    }

    @Override
    public List<UserSummary> usersRemovedByRevoking(UserId user, RoleId role) {
        return transactions.inTransaction(() -> {
            requireHeldRole(user, role);
            return orphansOf(user, role);
        });
    }

    private List<UserSummary> orphansOf(UserId user, RoleId role) {
        return grants.usersLosingAllRoles(Set.of(role), Optional.of(user));
    }

    /** Locks the user, then requires that they hold the role. */
    private void requireHeldRole(UserId user, RoleId role) {
        if (!users.lock(user)) {
            throw userNotFound(user);
        }
        boolean held = users.find(user).orElseThrow(() -> userNotFound(user)).applications().stream()
                .flatMap(a -> a.roles().stream())
                .anyMatch(r -> r.id().equals(role));
        if (!held) {
            throw new NotFoundException("User " + user.asString() + " does not hold role " + role.asString());
        }
    }


    /** Requires the application, then locks the roles against deletion; all of them must belong to it. */
    private void lockRolesOfApplication(ApplicationId application, Set<RoleId> roles) {
        if (applications.find(application).isEmpty()) {
            throw new NotFoundException("Application " + application.asString() + " not found");
        }
        if (applications.lockRolesAgainstDeletion(application, roles).size() != roles.size()) {
            throw new InvalidInputException(Map.of("roleId", "Roles must belong to the selected application"));
        }
    }

    private UserAccess requireUser(UserId id) {
        return users.find(id).orElseThrow(() -> userNotFound(id));
    }

    private static NotFoundException userNotFound(UserId id) {
        return new NotFoundException("User " + id.asString() + " not found");
    }
}
