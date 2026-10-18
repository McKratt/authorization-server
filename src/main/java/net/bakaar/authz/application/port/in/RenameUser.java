package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.Username;

public interface RenameUser {

    void renameUser(UserId id, Username username);
}
