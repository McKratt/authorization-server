package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserSummary;

public interface ApplicationChangePreview {

    List<UserSummary> usersRemovedByDeletingApplication(ApplicationId id);

    List<UserSummary> usersRemovedByDeletingRole(ApplicationId app, RoleId role);
}
