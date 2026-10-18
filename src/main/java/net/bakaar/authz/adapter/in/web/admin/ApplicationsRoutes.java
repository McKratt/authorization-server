package net.bakaar.authz.adapter.in.web.admin;

import static net.bakaar.authz.adapter.in.web.admin.AdminPages.model;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.ApplicationSummary;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.model.RoleDetails;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleName;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.port.in.CreateApplicationCommand;
import net.bakaar.authz.application.port.in.UpdateApplicationCommand;

/**
 * Applications tab and application dialogs, registered under {@code /admin/applications}.
 * <ul>
 *   <li>Creating, updating and deleting an application answer 204 with the {@code HX-Trigger} of
 *       {@link AdminPages#changed}.</li>
 *   <li>Role writes inside the dialog answer 200 with the refreshed dialog ({@link AdminPages#renderChanged}).</li>
 *   <li>Failed writes re-render their dialog: 422 for invalid input, 409 for a taken name. A removal that is no
 *       longer the confirmed one answers 409 with a fresh confirmation.</li>
 * </ul>
 * Unknown IDs end as 404 through {@link AdminPages#notFound}. The filter guards writes; the GET dialogs that only
 * lead to writes check the admin role here.
 */
public final class ApplicationsRoutes implements HttpService {

    static final String NAME_RULE = "Use 1 to 100 letters, digits, '.', '_' or '-', starting with a letter or digit.";
    static final String EMAIL_RULE = "Enter an email address like name@example.com.";
    static final String DESCRIPTION_RULE = "Use at most 2000 characters.";
    static final String APPLICATION_NAME_TAKEN = "This application name is already taken.";
    static final String ROLE_NAME_TAKEN = "This role name is already taken in this application.";

    private static final String DIALOG = "applications/dialog.jte";
    private static final String DIALOG_SLOT = "#dialog-slot";
    private static final Map<String, String> APPLICATION_CONFLICTS = Map.of("name", APPLICATION_NAME_TAKEN);
    private static final Map<String, String> ROLE_CONFLICTS = Map.of("roleName", ROLE_NAME_TAKEN);

    private final AdminPages pages;
    private final ApplicationAdminPorts applications;

    public ApplicationsRoutes(AdminPages pages, ApplicationAdminPorts applications) {
        this.pages = pages;
        this.applications = applications;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::applicationsTab)
                .post("/", this::createApplication)
                .get("/new", this::newApplicationDialog)
                .get("/{appId}", this::applicationDialog)
                .put("/{appId}", this::updateApplication)
                .delete("/{appId}", this::deleteApplication)
                .get("/{appId}/delete", this::deleteApplicationConfirmation)
                .get("/{appId}/role-options", this::roleOptions)
                .post("/{appId}/roles", this::addRole)
                .put("/{appId}/roles/{roleId}", this::updateRole)
                .delete("/{appId}/roles/{roleId}", this::deleteRole)
                .get("/{appId}/roles/{roleId}/delete", this::deleteRoleConfirmation);
    }

    private void applicationsTab(ServerRequest request, ServerResponse response) {
        pages.renderTab(request, response, "applications", "applications/tab.jte", Map.of(
                "session", AdminAuthFilter.session(request),
                "applications", applications.list().listApplications()));
    }

    /** Role checkboxes the new user and grant dialogs load when an application is chosen. */
    private void roleOptions(ServerRequest request, ServerResponse response) {
        pages.render(request, response, "users/role-options.jte", model(
                "application", applications.get().getApplication(appId(request)),
                "checked", Set.of()));
    }

    // ------------------------------------------------------------------ create

    private void newApplicationDialog(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        renderNewDialog(request, response, Status.OK_200, Map.of("name", "", "responsible", "", "description", ""),
                List.of(), Map.of());
    }

    /** {@code roleName} and {@code roleDescription} are parallel lists; rows left blank are ignored. */
    private void createApplication(ServerRequest request, ServerResponse response) {
        Parameters form = Forms.parse(request);
        FieldErrors errors = new FieldErrors();
        ApplicationName name = parseName(form, errors);
        Email responsible = parseResponsible(form, errors);
        Description description = parseDescription(form, errors);
        List<String> roleNames = form.all("roleName", List::of);
        List<String> roleDescriptions = form.all("roleDescription", List::of);
        List<RoleInput> inputs = new ArrayList<>();
        List<NewRole> roles = new ArrayList<>();
        for (int i = 0; i < Math.max(roleNames.size(), roleDescriptions.size()); i++) {
            String roleName = i < roleNames.size() ? roleNames.get(i).trim() : "";
            String roleDescription = i < roleDescriptions.size() ? roleDescriptions.get(i).trim() : "";
            if (roleName.isEmpty() && roleDescription.isEmpty()) {
                continue;
            }
            RoleName parsedName = errors.parse("roleName", NAME_RULE, () -> new RoleName(roleName));
            Description parsedDescription = errors.parse("roleDescription", DESCRIPTION_RULE,
                    () -> new Description(roleDescription));
            inputs.add(new RoleInput(roleName, roleDescription, parsedName == null, parsedDescription == null));
            if (parsedName != null && parsedDescription != null) {
                roles.add(new NewRole(parsedName, parsedDescription));
            }
        }
        if (errors.write(() -> applications.create().createApplication(
                new CreateApplicationCommand(name, responsible, description, roles)), APPLICATION_CONFLICTS)) {
            pages.changed(response);
            return;
        }
        renderNewDialog(request, response, errors.status(), values(form), inputs, errors.asMap());
    }

    private void renderNewDialog(ServerRequest request, ServerResponse response, Status status,
                                 Map<String, String> values, List<RoleInput> newRoles, Map<String, String> errors) {
        pages.render(request, response, status, DIALOG, model(
                "session", AdminAuthFilter.session(request),
                "application", null,
                "values", values,
                "newRoles", newRoles,
                "editedRoleId", null,
                "roleInput", null,
                "errors", errors));
    }

    // ------------------------------------------------------------------ application dialog and update

    private void applicationDialog(ServerRequest request, ServerResponse response) {
        ApplicationDetails application = applications.get().getApplication(appId(request));
        renderDialog(request, response, Status.OK_200, application, values(application), null, null, Map.of());
    }

    private void updateApplication(ServerRequest request, ServerResponse response) {
        ApplicationId id = appId(request);
        Parameters form = Forms.parse(request);
        FieldErrors errors = new FieldErrors();
        ApplicationName name = parseName(form, errors);
        Email responsible = parseResponsible(form, errors);
        Description description = parseDescription(form, errors);
        if (errors.write(() -> applications.update().updateApplication(
                new UpdateApplicationCommand(id, name, responsible, description)), APPLICATION_CONFLICTS)) {
            pages.changed(response);
            return;
        }
        renderDialog(request, response, errors.status(), applications.get().getApplication(id), values(form),
                null, null, errors.asMap());
    }

    /**
     * The filled-in dialog; read-only for readers.
     *
     * @param editedRoleId the role whose row shows {@code roleInput} and the role errors: a role ID, or empty for
     *                     the new role row; null when no role was submitted
     */
    private void renderDialog(ServerRequest request, ServerResponse response, Status status,
                              ApplicationDetails application, Map<String, String> values, String editedRoleId,
                              RoleInput roleInput, Map<String, String> errors) {
        pages.render(request, response, status, DIALOG, dialogModel(request, application, values, editedRoleId,
                roleInput, errors));
    }

    /** A role write succeeded: the refreshed dialog, and the open tab reloads. */
    private void renderRoleChanged(ServerRequest request, ServerResponse response, ApplicationId id) {
        ApplicationDetails application = applications.get().getApplication(id);
        pages.renderChanged(request, response, DIALOG, dialogModel(request, application, values(application),
                null, null, Map.of()));
    }

    private static Map<String, Object> dialogModel(ServerRequest request, ApplicationDetails application,
                                                   Map<String, String> values, String editedRoleId,
                                                   RoleInput roleInput, Map<String, String> errors) {
        return model(
                "session", AdminAuthFilter.session(request),
                "application", application,
                "values", values,
                "newRoles", List.of(),
                "editedRoleId", editedRoleId,
                "roleInput", roleInput,
                "errors", errors);
    }

    // ------------------------------------------------------------------ delete application

    private void deleteApplicationConfirmation(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        ApplicationId id = appId(request);
        ApplicationDetails application = applications.get().getApplication(id);
        renderDeleteApplicationConfirmation(request, response, Status.OK_200, application,
                applications.preview().usersRemovedByDeletingApplication(id));
    }

    private void deleteApplication(ServerRequest request, ServerResponse response) {
        ApplicationId id = appId(request);
        Set<UserId> confirmed = Forms.confirmedUserIds(request);
        try {
            applications.delete().deleteApplication(id, confirmed);
            pages.changed(response);
        } catch (UnconfirmedRemovalException e) {
            renderDeleteApplicationConfirmation(request, response, Status.CONFLICT_409,
                    applications.get().getApplication(id), e.wouldRemove());
        }
    }

    private void renderDeleteApplicationConfirmation(ServerRequest request, ServerResponse response, Status status,
                                                     ApplicationDetails application, List<UserSummary> removed) {
        String name = application.name().value();
        String message = removed.isEmpty()
                ? "Delete " + name + " with its roles and grants?"
                : "Deleting " + name + " also deletes the users left without a role: " + names(removed) + ".";
        pages.render(request, response, status, "confirm-dialog.jte", model(
                "title", "Delete application?",
                "message", message,
                "removed", removed,
                "deleteUrl", "/admin/applications/" + application.id().asString(),
                "hxTarget", DIALOG_SLOT,
                "confirmLabel", "Delete " + name));
    }

    // ------------------------------------------------------------------ roles

    private void addRole(ServerRequest request, ServerResponse response) {
        ApplicationId id = appId(request);
        Parameters form = Forms.parse(request);
        FieldErrors errors = new FieldErrors();
        NewRole role = parseRole(form, errors);
        if (errors.write(() -> applications.addRole().addRole(id, role), ROLE_CONFLICTS)) {
            renderRoleChanged(request, response, id);
            return;
        }
        renderRoleErrors(request, response, id, "", form, errors);
    }

    private void updateRole(ServerRequest request, ServerResponse response) {
        ApplicationId id = appId(request);
        RoleId roleId = roleId(request);
        Parameters form = Forms.parse(request);
        FieldErrors errors = new FieldErrors();
        NewRole role = parseRole(form, errors);
        if (errors.write(() -> applications.updateRole().updateRole(id, roleId, role), ROLE_CONFLICTS)) {
            renderRoleChanged(request, response, id);
            return;
        }
        renderRoleErrors(request, response, id, roleId.asString(), form, errors);
    }

    /** The dialog with the submitted role row kept and marked; the application fields come from the database. */
    private void renderRoleErrors(ServerRequest request, ServerResponse response, ApplicationId id,
                                  String editedRoleId, Parameters form, FieldErrors errors) {
        ApplicationDetails application = applications.get().getApplication(id);
        RoleInput input = new RoleInput(Forms.text(form, "name"), Forms.text(form, "description"),
                errors.asMap().containsKey("roleName"), errors.asMap().containsKey("roleDescription"));
        renderDialog(request, response, errors.status(), application, values(application), editedRoleId, input,
                errors.asMap());
    }

    private void deleteRoleConfirmation(ServerRequest request, ServerResponse response) {
        AdminPages.requireWriter(request);
        ApplicationId id = appId(request);
        RoleId roleId = roleId(request);
        List<UserSummary> removed = applications.preview().usersRemovedByDeletingRole(id, roleId);
        renderDeleteRoleConfirmation(request, response, Status.OK_200, id, roleId, removed);
    }

    private void deleteRole(ServerRequest request, ServerResponse response) {
        ApplicationId id = appId(request);
        RoleId roleId = roleId(request);
        Set<UserId> confirmed = Forms.confirmedUserIds(request);
        try {
            applications.deleteRole().deleteRole(id, roleId, confirmed);
            renderRoleChanged(request, response, id);
        } catch (UnconfirmedRemovalException e) {
            renderDeleteRoleConfirmation(request, response, Status.CONFLICT_409, id, roleId, e.wouldRemove());
        }
    }

    private void renderDeleteRoleConfirmation(ServerRequest request, ServerResponse response, Status status,
                                              ApplicationId id, RoleId roleId, List<UserSummary> removed) {
        ApplicationDetails application = applications.get().getApplication(id);
        String role = application.name().value() + " / " + roleOf(application, roleId).name().value();
        String message = removed.isEmpty()
                ? "Delete the role " + role + "?"
                : "Deleting " + role + " also deletes the users left without a role: " + names(removed) + ".";
        pages.render(request, response, status, "confirm-dialog.jte", model(
                "title", "Delete role?",
                "message", message,
                "removed", removed,
                "deleteUrl", "/admin/applications/" + id.asString() + "/roles/" + roleId.asString(),
                "hxTarget", DIALOG_SLOT,
                "confirmLabel", "Delete role"));
    }

    // ------------------------------------------------------------------ helpers

    private static ApplicationName parseName(Parameters form, FieldErrors errors) {
        return errors.parse("name", NAME_RULE, () -> new ApplicationName(Forms.text(form, "name")));
    }

    private static Email parseResponsible(Parameters form, FieldErrors errors) {
        return errors.parse("responsible", EMAIL_RULE, () -> new Email(Forms.text(form, "responsible")));
    }

    private static Description parseDescription(Parameters form, FieldErrors errors) {
        return errors.parse("description", DESCRIPTION_RULE, () -> new Description(Forms.text(form, "description")));
    }

    /** The {@code name} and {@code description} of a role form; null when either is invalid. */
    private static NewRole parseRole(Parameters form, FieldErrors errors) {
        RoleName name = errors.parse("roleName", NAME_RULE, () -> new RoleName(Forms.text(form, "name")));
        Description description = errors.parse("roleDescription", DESCRIPTION_RULE,
                () -> new Description(Forms.text(form, "description")));
        return name == null || description == null ? null : new NewRole(name, description);
    }

    /** The application fields as submitted. */
    private static Map<String, String> values(Parameters form) {
        return Map.of(
                "name", Forms.text(form, "name"),
                "responsible", Forms.text(form, "responsible"),
                "description", Forms.text(form, "description"));
    }

    /** The application fields as stored. */
    private static Map<String, String> values(ApplicationDetails application) {
        return Map.of(
                "name", application.name().value(),
                "responsible", application.responsible().value(),
                "description", application.description().value());
    }

    private static RoleDetails roleOf(ApplicationDetails application, RoleId roleId) {
        return application.roles().stream()
                .filter(role -> role.id().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Role " + roleId.asString() + " not found in application "
                        + application.id().asString()));
    }

    private static String names(List<UserSummary> users) {
        return users.stream().map(user -> user.username().value()).collect(Collectors.joining(", "));
    }

    private static ApplicationId appId(ServerRequest request) {
        return ApplicationId.parse(request.path().pathParameters().get("appId"));
    }

    private static RoleId roleId(ServerRequest request) {
        return RoleId.parse(request.path().pathParameters().get("roleId"));
    }

    /**
     * Lower-case text the client-side filter matches: the name, the responsible and the role names.
     */
    public static String filterText(ApplicationSummary application) {
        return Stream.concat(Stream.of(application.name().value(), application.responsible().value()),
                        application.roles().stream().map(role -> role.name().value()))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
