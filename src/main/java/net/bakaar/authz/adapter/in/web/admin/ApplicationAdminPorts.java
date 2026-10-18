package net.bakaar.authz.adapter.in.web.admin;

import net.bakaar.authz.application.port.in.AddRole;
import net.bakaar.authz.application.port.in.CreateApplication;
import net.bakaar.authz.application.port.in.DeleteApplication;
import net.bakaar.authz.application.port.in.DeleteRole;
import net.bakaar.authz.application.port.in.GetApplication;
import net.bakaar.authz.application.port.in.ListApplications;
import net.bakaar.authz.application.port.in.PreviewApplicationChanges;
import net.bakaar.authz.application.port.in.UpdateApplication;
import net.bakaar.authz.application.port.in.UpdateRole;

/**
 * The application use cases the admin UI calls, built in {@code Main}.
 */
public record ApplicationAdminPorts(ListApplications list, GetApplication get, CreateApplication create,
                                    UpdateApplication update, DeleteApplication delete, AddRole addRole,
                                    UpdateRole updateRole, DeleteRole deleteRole,
                                    PreviewApplicationChanges preview) {
}
