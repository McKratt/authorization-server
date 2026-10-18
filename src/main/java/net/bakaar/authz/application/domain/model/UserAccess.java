package net.bakaar.authz.application.domain.model;

import java.util.List;

/** A user with everything they may access; applications sorted by name. */
public record UserAccess(UserId id, Username username, List<ApplicationGrants> applications) {
}
