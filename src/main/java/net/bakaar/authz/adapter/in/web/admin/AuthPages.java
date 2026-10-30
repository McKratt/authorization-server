package net.bakaar.authz.adapter.in.web.admin;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * HTML pages the sign-in layer shows, rendered by the admin UI's templates (wired in {@code Main}).
 */
interface AuthPages {

    /**
     * 403 page for a signed-in user with neither admin role: who they are, and a sign-out button.
     */
    void forbidden(ServerRequest request, ServerResponse response, AdminSession session);

    /**
     * 400 page for a sign-in callback carrying an error instead of a code (e.g. cancelled at Keycloak), with a
     * link to sign in again. Shows nothing from the request.
     */
    void signInCancelled(ServerRequest request, ServerResponse response);
}
