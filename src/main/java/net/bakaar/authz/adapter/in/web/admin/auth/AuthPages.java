package net.bakaar.authz.adapter.in.web.admin.auth;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * HTML pages the sign-in layer shows, rendered by the admin UI's templates (wired in {@code Main}).
 */
public interface AuthPages {

    /**
     * 403 page for a signed-in user with neither admin role: who they are, and a sign-out button.
     */
    void forbidden(ServerRequest request, ServerResponse response, AdminSession session);
}
