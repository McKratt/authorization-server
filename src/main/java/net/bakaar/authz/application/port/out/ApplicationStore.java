package net.bakaar.authz.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.ApplicationSummary;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.RoleId;

public interface ApplicationStore {

    void insert(ApplicationId id, ApplicationName name, Email responsible, Description description);

    boolean update(ApplicationId id, ApplicationName name, Email responsible, Description description);

    boolean delete(ApplicationId id);

    Optional<ApplicationDetails> find(ApplicationId id);

    /** Sorted by name. */
    List<ApplicationSummary> findAll();

    void insertRole(ApplicationId app, RoleId id, NewRole role);

    boolean updateRole(ApplicationId app, RoleId id, NewRole role);

    boolean deleteRole(ApplicationId app, RoleId id);

    Set<RoleId> roleIds(ApplicationId app);
}
