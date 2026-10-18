package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;

public record UpdateApplicationCommand(ApplicationId id, ApplicationName name, Email responsible,
                                       Description description) {
}
