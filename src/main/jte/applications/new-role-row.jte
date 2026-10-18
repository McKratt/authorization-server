@import net.bakaar.authz.adapter.in.web.admin.RoleInput
@param RoleInput role
<%-- One initial role of the new application dialog; admin.js adds and removes these rows. --%>
<div class="role-row">
    @template.applications.role-fields(role = role, nameField = "roleName", descriptionField = "roleDescription", label = "new role")
    <button type="button" class="chip-x" data-remove-role aria-label="Remove this role">×</button>
</div>
