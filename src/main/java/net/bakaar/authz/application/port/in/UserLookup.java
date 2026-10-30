package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.UserAccess;
import net.bakaar.authz.application.domain.model.UserId;

public interface GetUser {

    UserAccess getUser(UserId id);
}
