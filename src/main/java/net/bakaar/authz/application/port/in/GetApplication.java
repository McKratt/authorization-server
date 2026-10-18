package net.bakaar.authz.application.port.in;

import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.NotFoundException;

public interface GetApplication {

    /** @throws NotFoundException when the application does not exist */
    ApplicationDetails getApplication(ApplicationId id);
}
