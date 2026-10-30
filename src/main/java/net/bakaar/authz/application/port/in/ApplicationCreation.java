package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.ApplicationId;

public interface ApplicationCreation {

    ApplicationId createApplication(CreateApplicationCommand command);
}
