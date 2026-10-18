package net.bakaar.authz.application.domain.model;

import net.bakaar.authz.common.Ids;

public record RoleId(long value) {

    public static RoleId parse(String text) {
        return new RoleId(Parsing.positiveId(text, "Role"));
    }

    public static RoleId next() {
        return new RoleId(Ids.next());
    }

    public String asString() {
        return Long.toString(value);
    }
}
