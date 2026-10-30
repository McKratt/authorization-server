package net.bakaar.authz.application.domain.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationSummary;
import net.bakaar.authz.application.domain.model.InvalidInputException;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.port.in.AddRole;
import net.bakaar.authz.application.port.in.CreateApplication;
import net.bakaar.authz.application.port.in.CreateApplicationCommand;
import net.bakaar.authz.application.port.in.DeleteApplication;
import net.bakaar.authz.application.port.in.DeleteRole;
import net.bakaar.authz.application.port.in.GetApplication;
import net.bakaar.authz.application.port.in.ListApplications;
import net.bakaar.authz.application.port.in.PreviewApplicationChanges;
import net.bakaar.authz.application.port.in.UpdateApplication;
import net.bakaar.authz.application.port.in.UpdateApplicationCommand;
import net.bakaar.authz.application.port.in.UpdateRole;
import net.bakaar.authz.application.port.out.ApplicationStore;
import net.bakaar.authz.application.port.out.GrantStore;
import net.bakaar.authz.application.port.out.Transactions;
import net.bakaar.authz.application.port.out.UserStore;

/**
 * Administers applications and their roles. Every use case, reads included, runs in one transaction.
 * A removal that would leave users without any role deletes those users, but only when the caller
 * confirmed exactly the users computed inside the same transaction.
 * <p>
 * Deletions lock what they delete first (application, then its roles, then the users holding them), so grants and
 * roles being added concurrently, which lock what they reference against deletion, are committed and counted
 * before the orphans are computed.
 */
public class ApplicationAdminService implements ListApplications, GetApplication, CreateApplication,
        UpdateApplication, DeleteApplication, AddRole, UpdateRole, DeleteRole, PreviewApplicationChanges {

    private final Transactions transactions;
    private final ApplicationStore applications;
    private final GrantStore grants;
    private final UserStore users;

    public ApplicationAdminService(Transactions transactions, ApplicationStore applications, GrantStore grants,
                                   UserStore users) {
        this.transactions = transactions;
        this.applications = applications;
        this.grants = grants;
        this.users = users;
    }

    @Override
    public List<ApplicationSummary> listApplications() {
        return transactions.inTransaction(applications::findAll);
    }

    @Override
    public ApplicationDetails getApplication(ApplicationId id) {
        return transactions.inTransaction(() -> requireApplication(id));
    }

    @Override
    public ApplicationId createApplication(CreateApplicationCommand command) {
        Set<String> names = new HashSet<>();
        for (NewRole role : command.roles()) {
            if (!names.add(role.name().value())) {
                throw new InvalidInputException(Map.of("roleName", "Role names must be unique"));
            }
        }
        return transactions.inTransaction(() -> {
            ApplicationId id = ApplicationId.next();
            applications.insert(id, command.name(), command.responsible(), command.description());
            command.roles().forEach(role -> applications.insertRole(id, RoleId.next(), role));
            return id;
        });
    }

    @Override
    public void updateApplication(UpdateApplicationCommand command) {
        transactions.run(() -> {
            if (!applications.update(command.id(), command.name(), command.responsible(), command.description())) {
                throw applicationNotFound(command.id());
            }
        });
    }

    @Override
    public void deleteApplication(ApplicationId id, Set<UserId> confirmedRemovals) {
        transactions.run(() -> {
            if (!applications.lockForDeletion(id)) {
                throw applicationNotFound(id);
            }
            Set<RoleId> removed = applications.lockRolesForDeletion(id);
            List<UserSummary> orphans = requireConfirmed(removed, confirmedRemovals);
            applications.delete(id);
            users.deleteUsersWithoutRoles(ids(orphans));
        });
    }

    @Override
    public RoleId addRole(ApplicationId app, NewRole role) {
        return transactions.inTransaction(() -> {
            if (!applications.lockAgainstDeletion(app)) {
                throw applicationNotFound(app);
            }
            RoleId id = RoleId.next();
            applications.insertRole(app, id, role);
            return id;
        });
    }

    @Override
    public void updateRole(ApplicationId app, RoleId role, NewRole update) {
        transactions.run(() -> {
            if (!applications.updateRole(app, role, update)) {
                throw roleNotFound(app, role);
            }
        });
    }

    @Override
    public void deleteRole(ApplicationId app, RoleId role, Set<UserId> confirmedRemovals) {
        transactions.run(() -> {
            if (!applications.lockRoleForDeletion(app, role)) {
                throw roleNotFound(app, role);
            }
            List<UserSummary> orphans = requireConfirmed(Set.of(role), confirmedRemovals);
            applications.deleteRole(app, role);
            users.deleteUsersWithoutRoles(ids(orphans));
        });
    }

    @Override
    public List<UserSummary> usersRemovedByDeletingApplication(ApplicationId id) {
        return transactions.inTransaction(() -> orphansOf(applicationRoleIds(id)));
    }

    @Override
    public List<UserSummary> usersRemovedByDeletingRole(ApplicationId app, RoleId role) {
        return transactions.inTransaction(() -> {
            requireRoleOf(app, role);
            return orphansOf(Set.of(role));
        });
    }

    private List<UserSummary> orphansOf(Set<RoleId> removed) {
        return grants.usersLosingAllRoles(removed, Optional.empty());
    }

    /**
     * With the removed roles already locked, locks the affected users, so a concurrent grant change waits and the
     * orphan query sees its outcome, then rejects the change unless the caller confirmed exactly the orphaned users.
     */
    private List<UserSummary> requireConfirmed(Set<RoleId> removed, Set<UserId> confirmedRemovals) {
        grants.lockUsersHolding(removed);
        List<UserSummary> orphans = orphansOf(removed);
        if (!ids(orphans).equals(confirmedRemovals)) {
            throw new UnconfirmedRemovalException(orphans);
        }
        return orphans;
    }

    private Set<RoleId> applicationRoleIds(ApplicationId id) {
        requireApplication(id);
        return applications.roleIds(id);
    }

    private ApplicationDetails requireApplication(ApplicationId id) {
        return applications.find(id).orElseThrow(() -> applicationNotFound(id));
    }

    private void requireRoleOf(ApplicationId app, RoleId role) {
        requireApplication(app);
        if (!applications.roleIds(app).contains(role)) {
            throw roleNotFound(app, role);
        }
    }

    private static Set<UserId> ids(List<UserSummary> summaries) {
        return summaries.stream().map(UserSummary::id).collect(Collectors.toSet());
    }

    private static NotFoundException applicationNotFound(ApplicationId id) {
        return new NotFoundException("Application " + id.asString() + " not found");
    }

    private static NotFoundException roleNotFound(ApplicationId app, RoleId role) {
        return new NotFoundException("Role " + role.asString() + " not found in application " + app.asString());
    }
}
