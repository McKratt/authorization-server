package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.UserSummary;

public interface UserChangePreview {

    List<UserSummary> usersRemovedByRevoking(UserId user, RoleId role);
}
