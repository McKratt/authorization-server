package net.bakaar.authz.adapter.in.web.admin;

import static net.bakaar.authz.adapter.in.web.admin.AdminPages.model;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationGrants;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleSummary;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserAccess;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.in.CreateUser;
import net.bakaar.authz.application.port.in.CreateUserCommand;
import net.bakaar.authz.application.port.in.DeleteUser;
import net.bakaar.authz.application.port.in.GetApplication;
import net.bakaar.authz.application.port.in.GetUser;
import net.bakaar.authz.application.port.in.GrantRoles;
import net.bakaar.authz.application.port.in.GrantRolesCommand;
import net.bakaar.authz.application.port.in.ListApplications;
import net.bakaar.authz.application.port.in.ListUsers;
import net.bakaar.authz.application.port.in.PreviewUserChanges;
import net.bakaar.authz.application.port.in.RenameUser;
import net.bakaar.authz.application.port.in.RevokeRole;
import net.bakaar.authz.application.port.in.RevokeRoleCommand;

/**
 * Users tab and user dialogs, registered under {@code /admin}. Dialog writes answer 204 with the
 * {@code HX-Trigger} of {@link AdminPages#changed}, or re-render their dialog: 422 for invalid input, 409 for a
 * taken username or a removal that is no longer the confirmed one. Unknown IDs end as 404 through
 * {@link AdminPages#notFound}. The filter guards writes; the GET dialogs that only lead to writes check the
 * admin role here.
 */
public final class UsersRoutes implements HttpService {

    static final String ENTER_USERNAME = "Enter a username of 1 to 255 characters.";
    static final String USERNAME_TAKEN = "This username is already taken.";
    static final String SELECT_APPLICATION = "Select an application.";
    static final String INVALID_ROLES = "Roles must belong to the selected application";

    private static final String DIALOG_SLOT = "#dialog-slot";

    private final AdminPages pages;
    private final ListUsers listUsers;
    private final GetUser getUser;
    private final CreateUser createUser;
    private final RenameUser renameUser;
    private final DeleteUser deleteUser;
    private final GrantRoles grantRoles;
    private final RevokeRole revokeRole;
    private final PreviewUserChanges previewUserChanges;
    private final ListApplications listApplications;
    private final GetApplication getApplication;

    public UsersRoutes(AdminPages pages, UserAdminPorts users, ApplicationAdminPorts applications) {
        this.pages = pages;
        this.listUsers = users.list();
        this.getUser = users.get();
        this.createUser = users.create();
        this.renameUser = users.rename();
        this.deleteUser = users.delete();
        this.grantRoles = users.grantRoles();
        this.revokeRole = users.revokeRole();
        this.previewUserChanges = users.preview();
        this.listApplications = applications.list();
        this.getApplication = applications.get();
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::usersTab)
                .get("/users/new", this::newUserDialog)
                .post("/users", this::createUser)
                .get("/users/{userId}", this::userDialog)
                .put("/users/{userId}", this::renameUser)
                .delete("/users/{userId}", this::deleteUser)
                .get("/users/{userId}/delete", this::deleteUserConfirmation)
                .get("/users/{userId}/grants/new", this::grantDialog)
                .post("/users/{userId}/grants", this::grantRoles)
                .delete("/users/{userId}/grants/{roleId}", this::revokeRole)
                .get("/users/{userId}/grants/{roleId}/delete", this::revokeConfirmation);
    }

    private void usersTab(ServerRequest request, ServerResponse response) {
        pages.renderTab(request, response, "users", "users/tab.jte", Map.of(
                "session", AdminAuthFilter.session(request),
                "users", listUsers.listUsers()));
    }

    // ------------------------------------------------------------------ new user and grant

    private void newUserDialog(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        renderGrantForm(request, response, Status.OK_200, null, "", "", Set.of(), Map.of());
    }

    private void createUser(ServerRequest request, ServerResponse response) {
        Parameters form = Forms.parse(request);
        String applicationId = Forms.text(form, "applicationId");
        FieldErrors errors = new FieldErrors();
        Username name = errors.parse("username", ENTER_USERNAME, () -> Forms.username(form));
        ApplicationId application = parseApplication(applicationId, errors);
        Set<RoleId> roles = parseRoles(form, errors);
        try {
            if (errors.write(() -> createUser.createUser(new CreateUserCommand(name, application, roles)),
                    Map.of("username", USERNAME_TAKEN))) {
                pages.changed(response);
                return;
            }
        } catch (NotFoundException e) {
            // the application was deleted in the meantime
            errors.add("applicationId", SELECT_APPLICATION);
        }
        renderGrantForm(request, response, errors.status(), null, Forms.text(form, "username"), applicationId,
                Forms.values(form, "roleId"), errors.asMap());
    }

    private void grantDialog(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        UserAccess user = getUser.getUser(userId(request));
        renderGrantForm(request, response, Status.OK_200, user, "", "", Set.of(), Map.of());
    }

    private void grantRoles(ServerRequest request, ServerResponse response) {
        UserId userId = userId(request);
        Parameters form = Forms.parse(request);
        String applicationId = Forms.text(form, "applicationId");
        FieldErrors errors = new FieldErrors();
        ApplicationId application = parseApplication(applicationId, errors);
        Set<RoleId> roles = parseRoles(form, errors);
        try {
            if (errors.write(() -> grantRoles.grantRoles(new GrantRolesCommand(userId, application, roles)),
                    Map.of())) {
                pages.changed(response);
                return;
            }
        } catch (NotFoundException e) {
            // either the user (getUser below answers 404) or the application is gone
            errors.add("applicationId", SELECT_APPLICATION);
        }
        renderGrantForm(request, response, errors.status(), getUser.getUser(userId), "",
                applicationId, Forms.values(form, "roleId"), errors.asMap());
    }

    /**
     * The new user dialog, or with a {@code user} the grant dialog. The chosen application's role checkboxes are
     * rendered too, keeping the ticked ones.
     */
    private void renderGrantForm(ServerRequest request, ServerResponse response, Status status, UserAccess user,
                                 String username, String applicationId, Set<String> checked,
                                 Map<String, String> errors) {
        pages.render(request, response, status, "users/new-dialog.jte", model(
                "user", user,
                "username", username,
                "applications", listApplications.listApplications(),
                "selected", selectedApplication(applicationId),
                "checked", checked,
                "errors", errors));
    }

    private ApplicationDetails selectedApplication(String applicationId) {
        if (applicationId.isEmpty()) {
            return null;
        }
        try {
            return getApplication.getApplication(ApplicationId.parse(applicationId));
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static ApplicationId parseApplication(String applicationId, FieldErrors errors) {
        return errors.parse("applicationId", SELECT_APPLICATION, () -> ApplicationId.parse(applicationId));
    }

    private static Set<RoleId> parseRoles(Parameters form, FieldErrors errors) {
        Set<RoleId> roles = errors.parse("roleId", INVALID_ROLES, () -> Forms.ids(form, "roleId", RoleId::parse));
        return roles == null ? Set.of() : roles;
    }

    // ------------------------------------------------------------------ user dialog, rename and delete

    private void userDialog(ServerRequest request, ServerResponse response) {
        UserAccess user = getUser.getUser(userId(request));
        renderUserDialog(request, response, Status.OK_200, user, user.username().value(), Map.of());
    }

    private void renameUser(ServerRequest request, ServerResponse response) {
        UserId userId = userId(request);
        Parameters form = Forms.parse(request);
        FieldErrors errors = new FieldErrors();
        Username name = errors.parse("username", ENTER_USERNAME, () -> Forms.username(form));
        if (errors.write(() -> renameUser.renameUser(userId, name), Map.of("username", USERNAME_TAKEN))) {
            pages.changed(response);
            return;
        }
        renderUserDialog(request, response, errors.status(), getUser.getUser(userId), Forms.text(form, "username"),
                errors.asMap());
    }

    private void renderUserDialog(ServerRequest request, ServerResponse response, Status status, UserAccess user,
                                  String username, Map<String, String> errors) {
        pages.render(request, response, status, "users/user-dialog.jte", model(
                "session", AdminAuthFilter.session(request),
                "user", user,
                "username", username,
                "errors", errors));
    }

    private void deleteUserConfirmation(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        UserAccess user = getUser.getUser(userId(request));
        String name = user.username().value();
        pages.render(request, response, "confirm-dialog.jte", model(
                "title", "Delete user?",
                "message", "Delete " + name + " and all their roles?",
                "removed", List.of(),
                "deleteUrl", "/admin/users/" + user.id().asString(),
                "hxTarget", DIALOG_SLOT,
                "confirmLabel", "Delete " + name));
    }

    private void deleteUser(ServerRequest request, ServerResponse response) {
        deleteUser.deleteUser(userId(request));
        pages.changed(response);
    }

    // ------------------------------------------------------------------ revoke

    private void revokeConfirmation(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        UserId userId = userId(request);
        RoleId roleId = roleId(request);
        List<UserSummary> removed = previewUserChanges.usersRemovedByRevoking(userId, roleId);
        renderRevokeConfirmation(request, response, Status.OK_200, userId, roleId, removed);
    }

    private void revokeRole(ServerRequest request, ServerResponse response) {
        UserId userId = userId(request);
        RoleId roleId = roleId(request);
        Set<UserId> confirmed = Forms.confirmedUserIds(request);
        try {
            revokeRole.revokeRole(new RevokeRoleCommand(userId, roleId, confirmed));
            pages.changed(response);
        } catch (UnconfirmedRemovalException e) {
            renderRevokeConfirmation(request, response, Status.CONFLICT_409, userId, roleId, e.wouldRemove());
        }
    }

    /**
     * "Remove role?" for one grant. With users to remove (only ever the user, whose last role it is), a warning
     * that the user is deleted too; otherwise a plain confirmation, also after a stale confirmation.
     */
    private void renderRevokeConfirmation(ServerRequest request, ServerResponse response, Status status,
                                          UserId userId, RoleId roleId, List<UserSummary> removed) {
        UserAccess user = getUser.getUser(userId);
        String username = user.username().value();
        String grant = grantName(user, roleId);
        String message = removed.isEmpty()
                ? "Remove " + grant + " from " + username + "?"
                : username + " has no other role. Removing " + grant + " also deletes the user "
                        + removed.stream().map(u -> u.username().value()).collect(Collectors.joining(", ")) + ".";
        pages.render(request, response, status, "confirm-dialog.jte", model(
                "title", "Remove role?",
                "message", message,
                "removed", removed,
                "deleteUrl", "/admin/users/" + userId.asString() + "/grants/" + roleId.asString(),
                "hxTarget", DIALOG_SLOT));
    }

    /** {@code app / role} of a role the user holds. */
    private static String grantName(UserAccess user, RoleId roleId) {
        for (ApplicationGrants app : user.applications()) {
            for (RoleSummary role : app.roles()) {
                if (role.id().equals(roleId)) {
                    return app.name().value() + " / " + role.name().value();
                }
            }
        }
        throw new NotFoundException("User " + user.id().asString() + " does not hold role " + roleId.asString());
    }

    // ------------------------------------------------------------------ helpers

    private static UserId userId(ServerRequest request) {
        return UserId.parse(request.path().pathParameters().get("userId"));
    }

    private static RoleId roleId(ServerRequest request) {
        return RoleId.parse(request.path().pathParameters().get("roleId"));
    }

    /**
     * Lower-case text the client-side filter matches: the username, application names and role names.
     */
    public static String filterText(UserAccess user) {
        return Stream.concat(Stream.of(user.username().value()), user.applications().stream()
                        .flatMap(app -> Stream.concat(Stream.of(app.name().value()),
                                app.roles().stream().map(role -> role.name().value()))))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
