@import java.util.List
@import java.util.Map
@import java.util.Set
@import net.bakaar.authz.domain.ApplicationDetails
@import net.bakaar.authz.domain.ApplicationSummary
@import net.bakaar.authz.domain.UserAccess
@param UserAccess user
@param String username
@param List<ApplicationSummary> applications
@param ApplicationDetails selected
@param Set<String> checked
@param Map<String, String> errors
<%-- New user dialog; with a user, the grant dialog: same form with the username fixed. --%>
!{String action = user == null ? "/admin/users" : "/admin/users/" + user.id().asString() + "/grants";}
!{String selectedId = selected == null ? "" : selected.id().asString();}
<dialog aria-labelledby="dialog-title">
<form hx-post="${action}" hx-target="#dialog-slot" data-requires-role novalidate>
    @if(user == null)
        <h2 id="dialog-title">New user</h2>
        <div class="field">
            <label for="username">Username</label>
            <input class="input" id="username" name="username" value="${username}" maxlength="255" autocomplete="off" required aria-invalid="${errors.containsKey("username") ? "true" : null}" aria-describedby="${errors.containsKey("username") ? "username-error" : null}">
            @if(errors.containsKey("username"))
                <p class="field-error" id="username-error">${errors.get("username")}</p>
            @endif
        </div>
    @else
        <h2 id="dialog-title">Grant roles to ${user.username().value()}</h2>
    @endif
    <div class="field">
        <label for="applicationId">1. Application</label>
        <select class="input" id="applicationId" name="applicationId" data-role-options="role-options" required aria-invalid="${errors.containsKey("applicationId") ? "true" : null}" aria-describedby="${errors.containsKey("applicationId") ? "applicationId-error" : null}">
            <option value="">Choose an application</option>
            @for(ApplicationSummary application : applications)
                <option value="${application.id().asString()}" selected="${application.id().asString().equals(selectedId)}">${application.name().value()}</option>
            @endfor
        </select>
        @if(errors.containsKey("applicationId"))
            <p class="field-error" id="applicationId-error">${errors.get("applicationId")}</p>
        @endif
    </div>
    <div class="field" id="role-options">
        @if(selected != null)
            @template.users.role-options(application = selected, checked = checked)
        @endif
    </div>
    @if(errors.containsKey("roleId"))
        <p class="field-error" id="roleId-error">${errors.get("roleId")}</p>
    @endif
    <div class="row">
        <button type="button" class="btn ghost" data-close-dialog>Cancel</button>
        <button type="submit" class="btn" disabled="${checked.isEmpty()}">${user == null ? "Create" : "Grant"}</button>
    </div>
</form>
</dialog>
