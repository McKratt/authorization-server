package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.Username;

public interface UserRenaming {

    void renameUser(UserId id, Username username);
}
