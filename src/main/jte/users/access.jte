@import net.bakaar.authz.adapter.in.web.admin.ApplicationColors
@import net.bakaar.authz.application.domain.model.ApplicationGrants
@import net.bakaar.authz.application.domain.model.RoleSummary
@import net.bakaar.authz.application.domain.model.UserAccess
@param UserAccess user
<%-- What a user may access, as application tags with their role chips. --%>
<dl class="access">
    @for(ApplicationGrants app : user.applications())
        !{String color = ApplicationColors.cssClass(app.id());}
        <dt><span class="apptag ${color}"><span class="dot" aria-hidden="true"></span>${app.name().value()}</span></dt>
        <dd>@for(RoleSummary role : app.roles())<span class="chip ${color}">${role.name().value()}</span>@endfor</dd>
    @endfor
</dl>
