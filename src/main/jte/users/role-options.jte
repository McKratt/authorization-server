@import java.util.Set
@import net.bakaar.authz.domain.ApplicationDetails
@import net.bakaar.authz.domain.RoleDetails
@param ApplicationDetails application
@param Set<String> checked
<%-- Role checkboxes of one application, swapped into #role-options when the application changes. --%>
<fieldset class="roles">
    <legend>2. Roles of ${application.name().value()} (at least one)</legend>
    @if(application.roles().isEmpty())
        <p class="muted">${application.name().value()} has no roles yet.</p>
    @endif
    @for(RoleDetails role : application.roles())
        <label class="check">
            <input type="checkbox" name="roleId" value="${role.id().asString()}" checked="${checked.contains(role.id().asString())}">
            <span>${role.name().value()}@if(!role.description().value().isEmpty())<span class="muted">: ${role.description().value()}</span>@endif</span>
        </label>
    @endfor
</fieldset>
