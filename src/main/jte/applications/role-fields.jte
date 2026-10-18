@import net.bakaar.authz.adapter.in.web.admin.RoleInput
@param RoleInput role
@param String nameField
@param String descriptionField
@param String label
<%-- Name and description inputs of one role row; an invalid one points at the role error below the rows. --%>
<input class="input role-name" name="${nameField}" value="${role.name()}" placeholder="name" aria-label="Name of ${label}" maxlength="100" autocomplete="off" aria-invalid="${role.invalidName() ? "true" : null}" aria-describedby="${role.invalidName() ? "roleName-error" : null}">
<input class="input role-description" name="${descriptionField}" value="${role.description()}" placeholder="description" aria-label="Description of ${label}" maxlength="2000" autocomplete="off" aria-invalid="${role.invalidDescription() ? "true" : null}" aria-describedby="${role.invalidDescription() ? "roleDescription-error" : null}">
