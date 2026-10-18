package net.bakaar.authz.application.port.in;

import java.util.List;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.NewRole;

public record CreateApplicationCommand(ApplicationName name, Email responsible, Description description,
                                       List<NewRole> roles) {
}
