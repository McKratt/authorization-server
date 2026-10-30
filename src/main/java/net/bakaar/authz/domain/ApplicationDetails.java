package net.bakaar.authz.application.domain.model;

import java.util.List;

public record ApplicationDetails(ApplicationId id, ApplicationName name, Email responsible,
                                 Description description, List<RoleDetails> roles) {
}
