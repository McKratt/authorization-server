package net.bakaar.authz.application.domain.model;

import java.util.List;

/** The roles a user holds in one application; roles sorted by name. */
public record ApplicationGrants(ApplicationId id, ApplicationName name, List<RoleSummary> roles) {
}
