package net.bakaar.authz.domain;

public record RoleDetails(RoleId id, RoleName name, Description description, int userCount) {
}
