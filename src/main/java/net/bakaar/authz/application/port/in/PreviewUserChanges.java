package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;

public interface PreviewUserChanges {

    List<UserSummary> usersRemovedByRevoking(UserId user, RoleId role);
}
