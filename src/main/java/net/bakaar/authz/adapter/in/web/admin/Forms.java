package net.bakaar.authz.adapter.in.web.admin;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.BadRequestException;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.Username;

/**
 * Reads the urlencoded forms htmx sends. Text fields are trimmed; a missing field reads as empty.
 */
final class Forms {

    private static final String COMPONENT = "form-params";

    private Forms() {
    }

    /**
     * The urlencoded body, or no parameters when there is no body (htmx puts DELETE parameters in the query).
     *
     * @throws HttpException 415 for another content type, 400 for a malformed body
     */
    static Parameters parse(ServerRequest request) {
        if (!request.content().hasEntity()) {
            return Parameters.empty(COMPONENT);
        }
        boolean urlencoded = request.headers().contentType()
                .filter(type -> type.test(MediaTypes.APPLICATION_FORM_URLENCODED))
                .isPresent();
        if (!urlencoded) {
            throw new HttpException("Expected a urlencoded form", Status.UNSUPPORTED_MEDIA_TYPE_415);
        }
        try {
            return request.content().as(Parameters.class);
        } catch (IllegalArgumentException e) {
            // URLDecoder rejects malformed escapes such as %zz
            throw new BadRequestException("Malformed form");
        }
    }

    /** The first value of a field, trimmed; empty when the field is missing. */
    static String text(Parameters form, String name) {
        return form.first(name).map(String::trim).orElse("");
    }

    /** The trimmed {@code username} field. The record does not trim, so that token usernames stay exact. */
    static Username username(Parameters form) {
        return new Username(text(form, "username"));
    }

    /** Every non-blank value of a repeated field, trimmed and in order. */
    static Set<String> values(Parameters form, String name) {
        Set<String> values = new LinkedHashSet<>();
        for (String value : form.all(name, List::of)) {
            if (!value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    /**
     * The {@code confirmedUserIds} of a removal: htmx sends DELETE parameters in the query string, other clients
     * may send a form body, so both are read.
     */
    static Set<UserId> confirmedUserIds(ServerRequest request) {
        Set<UserId> confirmed = new LinkedHashSet<>(ids(request.query(), "confirmedUserIds", UserId::parse));
        confirmed.addAll(ids(parse(request), "confirmedUserIds", UserId::parse));
        return confirmed;
    }

    /**
     * Every non-blank value of a repeated field, parsed; the parser's exception (e.g. {@code NotFoundException}
     * for a malformed ID) propagates.
     */
    static <T> Set<T> ids(Parameters form, String name, Function<String, T> parser) {
        Set<T> ids = new LinkedHashSet<>();
        for (String value : values(form, name)) {
            ids.add(parser.apply(value));
        }
        return ids;
    }
}
