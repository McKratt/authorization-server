package net.bakaar.authz.domain;

import net.bakaar.authz.common.Ids;

public record ApplicationId(long value) {

    public static ApplicationId parse(String text) {
        return new ApplicationId(Parsing.positiveId(text, "Application"));
    }

    public static ApplicationId next() {
        return new ApplicationId(Ids.next());
    }

    public String asString() {
        return Long.toString(value);
    }
}
