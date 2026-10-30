package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserSummary;

public interface PreviewApplicationChanges {

    List<UserSummary> usersRemovedByDeletingApplication(ApplicationId id);

    List<UserSummary> usersRemovedByDeletingRole(ApplicationId app, RoleId role);
}
