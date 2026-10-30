package net.bakaar.authz.domain;

import java.util.List;

public record ApplicationSummary(ApplicationId id, ApplicationName name, Email responsible,
                                 List<RoleSummary> roles, int userCount) {

    public ApplicationSummary {
        roles = List.copyOf(roles);
    }
}
