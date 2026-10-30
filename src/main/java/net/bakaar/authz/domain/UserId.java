package net.bakaar.authz.application.domain.model;

import net.bakaar.authz.common.Ids;

public record UserId(long value) {

    public static UserId parse(String text) {
        return new UserId(Parsing.positiveId(text, "User"));
    }

    public static UserId next() {
        return new UserId(Ids.next());
    }

    public String asString() {
        return Long.toString(value);
    }
}
