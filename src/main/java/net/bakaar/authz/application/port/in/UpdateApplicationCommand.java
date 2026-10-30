package net.bakaar.authz.application.port.in;

import java.util.Objects;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Description;
import net.bakaar.authz.domain.Email;

public record UpdateApplicationCommand(ApplicationId id, ApplicationName name, Email responsible,
                                       Description description) {

    public UpdateApplicationCommand {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(responsible, "responsible must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
