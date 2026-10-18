package net.bakaar.authz.adapter.in.web.admin;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminSession;

/**
 * Users tab, registered under {@code /admin}. Placeholder page until the jte templates exist: it only shows who is
 * signed in and carries the CSRF token.
 */
public final class UsersRoutes implements HttpService {

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::usersTab);
    }

    private void usersTab(ServerRequest request, ServerResponse response) {
        AdminSession session = AdminAuthFilter.session(request);
        response.header(HeaderNames.CACHE_CONTROL, "no-store");
        response.headers().contentType(MediaTypes.TEXT_HTML);
        response.send("""
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="csrf-token" content="%s">
                <title>Authorizations</title>
                </head>
                <body>
                <p>Signed in as %s</p>
                </body>
                </html>
                """.formatted(escape(session.csrfToken()), escape(session.username())));
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
