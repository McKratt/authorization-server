@import java.util.List
@import net.bakaar.authz.adapter.in.web.admin.AdminSession
@import net.bakaar.authz.domain.ApplicationSummary
@param AdminSession session
@param List<ApplicationSummary> applications
<div class="bar">
    @if(session.canWrite())
        <button type="button" class="btn" hx-get="/admin/applications/new" hx-target="#dialog-slot">+ New application</button>
    @endif
    <input type="search" id="filter" class="input" placeholder="Filter apps, responsibles, roles…" aria-label="Filter applications, responsibles and roles" autocomplete="off">
</div>
@template.applications.table(applications = applications)
