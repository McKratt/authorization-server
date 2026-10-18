@import java.util.List
@import java.util.Map
@import net.bakaar.authz.adapter.in.web.admin.ApplicationColors
@import net.bakaar.authz.adapter.in.web.admin.RoleInput
@import net.bakaar.authz.adapter.in.web.admin.auth.AdminSession
@import net.bakaar.authz.application.domain.model.ApplicationDetails
@import net.bakaar.authz.application.domain.model.RoleDetails
@param AdminSession session
@param ApplicationDetails application
@param Map<String, String> values
@param List<RoleInput> newRoles
@param String editedRoleId
@param RoleInput roleInput
@param Map<String, String> errors
<%--
    Application dialog. Without an application: the new application form, whose initial roles are posted with it.
    With one: the edit form, where each role is saved, added or deleted on its own and the dialog is re-rendered;
    editedRoleId names the role row (empty: the new role row) that shows roleInput and the role errors.
    Readers get the same content read-only.
--%>
<dialog aria-labelledby="dialog-title" class="wide">
@if(application == null)
    <form hx-post="/admin/applications" hx-target="#dialog-slot" novalidate>
        <h2 id="dialog-title">New application</h2>
        @template.applications.fields(values = values, errors = errors, renaming = false)
        <fieldset class="roles">
            <legend>Roles</legend>
            <div data-role-rows>
                @for(RoleInput role : newRoles)
                    @template.applications.new-role-row(role = role)
                @endfor
                @if(newRoles.isEmpty())
                    @template.applications.new-role-row(role = RoleInput.EMPTY)
                @endif
            </div>
            <template data-role-row-template>@template.applications.new-role-row(role = RoleInput.EMPTY)</template>
            @template.applications.role-errors(errors = errors)
            <button type="button" class="btn ghost" data-add-role>+ Add role</button>
        </fieldset>
        <div class="row">
            <button type="button" class="btn ghost" data-close-dialog>Cancel</button>
            <button type="submit" class="btn">Create</button>
        </div>
    </form>
@else
    !{String appUrl = "/admin/applications/" + application.id().asString();}
    !{String color = ApplicationColors.cssClass(application.id());}
    @if(session.canWrite())
        <form id="application-form" hx-put="${appUrl}" hx-target="#dialog-slot" novalidate>
            <h2 id="dialog-title">Application ${application.name().value()}</h2>
            @template.applications.fields(values = values, errors = errors, renaming = true)
        </form>
        <section class="roles" aria-labelledby="roles-title">
            <h3 id="roles-title">Roles</h3>
            @if(!application.roles().isEmpty())
                <p class="note" id="role-rename-note">Renaming a role changes the role names client applications receive from the authorization API.</p>
            @endif
            @for(RoleDetails role : application.roles())
                !{String roleId = role.id().asString();}
                !{boolean edited = roleId.equals(editedRoleId);}
                <form class="role-row" hx-put="${appUrl}/roles/${roleId}" hx-target="#dialog-slot" novalidate>
                    @template.applications.role-fields(role = edited ? roleInput : new RoleInput(role.name().value(), role.description().value(), false, false), nameField = "name", descriptionField = "description", label = "role " + role.name().value())
                    <span class="count muted">${role.userCount()} ${role.userCount() == 1 ? "user" : "users"}</span>
                    <button type="submit" class="btn ghost">Save</button>
                    <button type="button" class="chip-x" hx-get="${appUrl}/roles/${roleId}/delete" hx-target="#dialog-slot" aria-label="Delete role ${role.name().value()}">×</button>
                </form>
                @if(edited)
                    @template.applications.role-errors(errors = errors)
                @endif
            @endfor
            <form class="role-row" hx-post="${appUrl}/roles" hx-target="#dialog-slot" novalidate>
                @template.applications.role-fields(role = "".equals(editedRoleId) ? roleInput : RoleInput.EMPTY, nameField = "name", descriptionField = "description", label = "new role")
                <button type="submit" class="btn ghost">Add</button>
            </form>
            @if("".equals(editedRoleId))
                @template.applications.role-errors(errors = errors)
            @endif
        </section>
        <div class="row">
            <button type="button" class="btn danger" hx-get="${appUrl}/delete" hx-target="#dialog-slot">Delete application</button>
            <span class="spacer"></span>
            <button type="button" class="btn ghost" data-close-dialog>Cancel</button>
            <button type="submit" class="btn" form="application-form">Save</button>
        </div>
    @else
        <h2 id="dialog-title"><span class="apptag ${color}"><span class="dot" aria-hidden="true"></span>${application.name().value()}</span></h2>
        <dl class="details">
            <dt>Responsible</dt>
            <dd>${application.responsible().value()}</dd>
            <dt>Description</dt>
            <dd>@if(application.description().value().isEmpty())<span class="muted">None</span>@else${application.description().value()}@endif</dd>
        </dl>
        <h3>Roles</h3>
        @if(application.roles().isEmpty())
            <p class="muted">No roles yet.</p>
        @endif
        <ul class="role-list">
            @for(RoleDetails role : application.roles())
                <li>
                    <span class="chip ${color}">${role.name().value()}</span>
                    @if(!role.description().value().isEmpty())<span>${role.description().value()}</span>@endif
                    <span class="count muted">${role.userCount()} ${role.userCount() == 1 ? "user" : "users"}</span>
                </li>
            @endfor
        </ul>
        <div class="row">
            <button type="button" class="btn ghost" data-close-dialog>Close</button>
        </div>
    @endif
@endif
</dialog>
