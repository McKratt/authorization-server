package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.application.domain.model.ApplicationSummary;

public interface ListApplications {

    List<ApplicationSummary> listApplications();
}
