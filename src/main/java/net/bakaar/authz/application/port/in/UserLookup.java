package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.UserAccess;
import net.bakaar.authz.domain.UserId;

public interface UserLookup {

    UserAccess getUser(UserId id);
}
