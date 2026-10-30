package net.bakaar.authz.domain;

import java.util.List;

public record ApplicationDetails(ApplicationId id, ApplicationName name, Email responsible,
                                 Description description, List<RoleDetails> roles) {

    public ApplicationDetails {
        roles = List.copyOf(roles);
    }
}
