package net.bakaar.authz.domain;

import java.util.List;

/** The roles a user holds in one application; roles sorted by name. */
public record ApplicationGrants(ApplicationId id, ApplicationName name, List<RoleSummary> roles) {

    public ApplicationGrants {
        roles = List.copyOf(roles);
    }
}
