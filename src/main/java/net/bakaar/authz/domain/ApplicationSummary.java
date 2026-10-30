package net.bakaar.authz.application.domain.model;

import java.util.List;

public record ApplicationSummary(ApplicationId id, ApplicationName name, Email responsible,
                                 List<RoleSummary> roles, int userCount) {
}
