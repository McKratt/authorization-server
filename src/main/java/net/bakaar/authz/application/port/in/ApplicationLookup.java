package net.bakaar.authz.application.port.in;

import net.bakaar.authz.domain.ApplicationDetails;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.NotFoundException;

public interface ApplicationLookup {

    /** @throws NotFoundException when the application does not exist */
    ApplicationDetails getApplication(ApplicationId id);
}
