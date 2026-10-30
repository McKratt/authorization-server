@import java.util.List
@import net.bakaar.authz.adapter.in.web.admin.AdminSession
@import net.bakaar.authz.domain.UserAccess
@param AdminSession session
@param List<UserAccess> users
<div class="bar">
    @if(session.canWrite())
        <button type="button" class="btn" hx-get="/admin/users/new" hx-target="#dialog-slot">+ New user</button>
    @endif
    <input type="search" id="filter" class="input" placeholder="Filter users, apps, roles…" aria-label="Filter users, applications and roles" autocomplete="off">
</div>
@template.users.table(session = session, users = users)
