package net.bakaar.authz.application.domain.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.bakaar.authz.application.domain.model.InvalidInputException;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserAccess;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.in.CreateUser;
import net.bakaar.authz.application.port.in.CreateUserCommand;
import net.bakaar.authz.application.port.in.DeleteUser;
import net.bakaar.authz.application.port.in.GetUser;
import net.bakaar.authz.application.port.in.GrantRoles;
import net.bakaar.authz.application.port.in.GrantRolesCommand;
import net.bakaar.authz.application.port.in.ListUsers;
import net.bakaar.authz.application.port.in.PreviewUserChanges;
import net.bakaar.authz.application.port.in.RenameUser;
import net.bakaar.authz.application.port.in.RevokeRole;
import net.bakaar.authz.application.port.in.RevokeRoleCommand;
import net.bakaar.authz.application.port.out.ApplicationStore;
import net.bakaar.authz.application.port.out.GrantStore;
import net.bakaar.authz.application.port.out.Transactions;
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
public class UserAdminService implements ListUsers, GetUser, CreateUser, RenameUser, DeleteUser, GrantRoles,
        RevokeRole, PreviewUserChanges {

    private final Transactions transactions;
    private final UserStore users;
    private final ApplicationStore applications;
    private final GrantStore grants;

    public UserAdminService(Transactions transactions, UserStore users, ApplicationStore applications,
                            GrantStore grants) {
        this.transactions = transactions;
        this.users = users;
        this.applications = applications;
        this.grants = grants;
    }

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
        requireRoles(command.roles());
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
        requireRoles(command.roles());
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
            List<UserSummary> orphans = orphansOf(command.user(), command.role());
            if (!ids(orphans).equals(command.confirmedRemovals())) {
                throw new UnconfirmedRemovalException(orphans);
            }
            grants.revoke(command.user(), command.role());
            users.deleteUsersWithoutRoles(ids(orphans));
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

    private static void requireRoles(Set<RoleId> roles) {
        if (roles.isEmpty()) {
            throw new InvalidInputException(Map.of("roleId", "Select at least one role"));
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

    private static Set<UserId> ids(List<UserSummary> summaries) {
        return summaries.stream().map(UserSummary::id).collect(Collectors.toSet());
    }

    private static NotFoundException userNotFound(UserId id) {
        return new NotFoundException("User " + id.asString() + " not found");
    }
}
