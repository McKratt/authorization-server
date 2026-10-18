@import java.util.List
@import net.bakaar.authz.adapter.in.web.admin.ApplicationColors
@import net.bakaar.authz.adapter.in.web.admin.UsersRoutes
@import net.bakaar.authz.adapter.in.web.admin.auth.AdminSession
@import net.bakaar.authz.application.domain.model.ApplicationGrants
@import net.bakaar.authz.application.domain.model.RoleSummary
@import net.bakaar.authz.application.domain.model.UserAccess
@param AdminSession session
@param List<UserAccess> users
<div class="card">
<table class="grid">
    <thead>
    <tr>
        <th scope="col">User</th>
        <th scope="col">Application</th>
        <th scope="col">Roles</th>
        @if(session.canWrite())
            <th scope="col"><span class="visually-hidden">Actions</span></th>
        @endif
    </tr>
    </thead>
    @if(users.isEmpty())
        <tbody>
        <tr><td class="empty muted" colspan="4">No users yet.</td></tr>
        </tbody>
    @endif
    @for(UserAccess user : users)
        !{String userUrl = "/admin/users/" + user.id().asString();}
        !{int rows = Math.max(1, user.applications().size());}
        <tbody data-filter="${UsersRoutes.filterText(user)}">
        @for(int i = 0; i < rows; i++)
            <tr>
                @if(i == 0)
                    <th scope="rowgroup" rowspan="${rows}">
                        <button type="button" class="link" hx-get="${userUrl}" hx-target="#dialog-slot">${user.username().value()}</button>
                    </th>
                @endif
                @if(user.applications().isEmpty())
                    <td></td><td></td>
                @else
                    !{ApplicationGrants app = user.applications().get(i);}
                    !{String color = ApplicationColors.cssClass(app.id());}
                    <td>
                        <button type="button" class="link apptag ${color}" hx-get="/admin/applications/${app.id().asString()}" hx-target="#dialog-slot"><span class="dot" aria-hidden="true"></span>${app.name().value()}</button>
                    </td>
                    <td>
                        @for(RoleSummary role : app.roles())
                            <span class="chip ${color}">${role.name().value()}@if(session.canWrite())<button type="button" class="chip-x" hx-get="${userUrl}/grants/${role.id().asString()}/delete" hx-target="#dialog-slot" aria-label="Revoke ${app.name().value()} / ${role.name().value()} from ${user.username().value()}">×</button>@endif</span>
                        @endfor
                    </td>
                @endif
                @if(i == 0 && session.canWrite())
                    <td rowspan="${rows}" class="actions">
                        <button type="button" class="btn ghost" hx-get="${userUrl}/grants/new" hx-target="#dialog-slot">+ Grant</button>
                    </td>
                @endif
            </tr>
        @endfor
        </tbody>
    @endfor
</table>
</div>
