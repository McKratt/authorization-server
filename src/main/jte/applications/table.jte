@import java.util.List
@import net.bakaar.authz.adapter.in.web.admin.ApplicationColors
@import net.bakaar.authz.adapter.in.web.admin.ApplicationsRoutes
@import net.bakaar.authz.application.domain.model.ApplicationSummary
@import net.bakaar.authz.application.domain.model.RoleSummary
@param List<ApplicationSummary> applications
<%-- Every application, including those nobody uses yet. The name opens the application dialog. --%>
<div class="card">
<table class="grid">
    <thead>
    <tr>
        <th scope="col">Application</th>
        <th scope="col">Responsible</th>
        <th scope="col">Roles</th>
        <th scope="col" class="num">Users</th>
    </tr>
    </thead>
    <tbody>
    @if(applications.isEmpty())
        <tr><td class="empty muted" colspan="4">No applications yet.</td></tr>
    @endif
    @for(ApplicationSummary application : applications)
        !{String color = ApplicationColors.cssClass(application.id());}
        <tr data-filter="${ApplicationsRoutes.filterText(application)}">
            <th scope="row">
                <button type="button" class="link apptag ${color}" hx-get="/admin/applications/${application.id().asString()}" hx-target="#dialog-slot"><span class="dot" aria-hidden="true"></span>${application.name().value()}</button>
            </th>
            <td>${application.responsible().value()}</td>
            <td>@for(RoleSummary role : application.roles())<span class="chip ${color}">${role.name().value()}</span>@endfor</td>
            <td class="num">${application.userCount()}</td>
        </tr>
    @endfor
    </tbody>
</table>
</div>
