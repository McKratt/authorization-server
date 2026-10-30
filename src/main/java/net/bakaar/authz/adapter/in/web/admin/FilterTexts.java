package net.bakaar.authz.adapter.in.web.admin;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bakaar.authz.domain.ApplicationSummary;
import net.bakaar.authz.domain.UserAccess;

/**
 * Lower-case text the client-side table filter matches against ({@code data-filter}). Public because the
 * precompiled jte templates, generated in another package, call it.
 */
public final class FilterTexts {

    private FilterTexts() {
    }

    public static String of(UserAccess user) {
        return Stream.concat(Stream.of(user.username().value()), user.applications().stream()
                        .flatMap(app -> Stream.concat(Stream.of(app.name().value()),
                                app.roles().stream().map(role -> role.name().value()))))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    public static String of(ApplicationSummary application) {
        return Stream.concat(Stream.of(application.name().value(), application.responsible().value()),
                        application.roles().stream().map(role -> role.name().value()))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
