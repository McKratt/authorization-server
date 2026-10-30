@import java.util.Map
@import net.bakaar.authz.adapter.in.web.admin.AdminSession
@import net.bakaar.authz.domain.UserAccess
@param AdminSession session
@param UserAccess user
@param String username
@param Map<String, String> errors
<%-- User dialog: rename and delete for admins, read-only for readers. --%>
!{String userUrl = "/admin/users/" + user.id().asString();}
<dialog aria-labelledby="dialog-title">
@if(session.canWrite())
    <form hx-put="${userUrl}" hx-target="#dialog-slot" novalidate>
        <h2 id="dialog-title">User ${user.username().value()}</h2>
        <div class="field">
            <label for="username">Username</label>
            <input class="input" id="username" name="username" value="${username}" maxlength="255" autocomplete="off" required aria-invalid="${errors.containsKey("username") ? "true" : null}" aria-describedby="${errors.containsKey("username") ? "username-error" : null}">
            @if(errors.containsKey("username"))
                <p class="field-error" id="username-error">${errors.get("username")}</p>
            @endif
        </div>
        @template.users.access(user = user)
        <div class="row">
            <button type="button" class="btn danger" hx-get="${userUrl}/delete" hx-target="#dialog-slot">Delete user</button>
            <button type="button" class="btn ghost" hx-get="${userUrl}/grants/new" hx-target="#dialog-slot">+ Grant</button>
            <span class="spacer"></span>
            <button type="button" class="btn ghost" data-close-dialog>Cancel</button>
            <button type="submit" class="btn">Save</button>
        </div>
    </form>
@else
    <h2 id="dialog-title">User ${user.username().value()}</h2>
    @template.users.access(user = user)
    <div class="row">
        <button type="button" class="btn ghost" data-close-dialog>Close</button>
    </div>
@endif
</dialog>
