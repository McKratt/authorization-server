package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.UserId;

public interface DeleteUser {

    void deleteUser(UserId id);
}
