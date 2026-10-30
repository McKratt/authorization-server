package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.domain.ApplicationSummary;

public interface ApplicationListing {

    List<ApplicationSummary> listApplications();
}
