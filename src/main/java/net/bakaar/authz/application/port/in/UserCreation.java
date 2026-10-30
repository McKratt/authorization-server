package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.UserId;

public interface UserCreation {

    UserId createUser(CreateUserCommand command);
}
