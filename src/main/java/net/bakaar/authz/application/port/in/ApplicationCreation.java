package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.ApplicationId;

public interface CreateApplication {

    ApplicationId createApplication(CreateApplicationCommand command);
}
