package net.bakaar.authz.adapter.in.web.api;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bakaar.authz.application.domain.model.AccessToken;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Authorization;
import net.bakaar.authz.application.domain.model.InvalidAccessTokenException;
import net.bakaar.authz.application.domain.model.Role;
import net.bakaar.authz.application.port.in.GetAuthorization;
import net.bakaar.authz.application.port.in.GetAuthorizationQuery;

/**
 * {@code GET ?application=<name>} with {@code Authorization: Bearer <jwt>}.
 * <ul>
 *     <li>200: roles of the token's owner for that application</li>
 *     <li>400: missing, repeated or invalid {@code application} parameter</li>
 *     <li>401: missing, repeated or untrusted bearer token</li>
 *     <li>404: the user holds no role for that application (also for unknown users or applications)</li>
 * </ul>
 * There is deliberately no way to name the user: it comes from the verified token only.
 */
public class AuthorizationApi implements HttpService {

    private static final Logger LOGGER = Logger.getLogger(AuthorizationApi.class.getName());
    private static final String BEARER_PREFIX = "bearer ";
    private static final String APPLICATION_PARAM = "application";

    private final GetAuthorization getAuthorization;

    public AuthorizationApi(GetAuthorization getAuthorization) {
        this.getAuthorization = getAuthorization;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getAuthorization);
    }

    private void getAuthorization(ServerRequest request, ServerResponse response) {
        response.header(HeaderNames.CACHE_CONTROL, "no-store");

        Optional<AccessToken> accessToken = bearerToken(request);
        if (accessToken.isEmpty()) {
            unauthorized(response, null);
            return;
        }
        Optional<ApplicationName> application = applicationName(request);
        if (application.isEmpty()) {
            error(response, Status.BAD_REQUEST_400, "invalid_request");
            return;
        }

        try {
            getAuthorization.getAuthorization(new GetAuthorizationQuery(accessToken.get(), application.get()))
                    .ifPresentOrElse(
                            authorization -> response.send(toJson(authorization)),
                            () -> error(response, Status.NOT_FOUND_404, "not_found"));
        } catch (InvalidAccessTokenException e) {
            LOGGER.log(Level.FINE, e.getMessage());
            unauthorized(response, "invalid_token");
        }
    }

    private static Optional<AccessToken> bearerToken(ServerRequest request) {
        List<String> values = request.headers().all(HeaderNames.AUTHORIZATION, List::of);
        if (values.size() != 1) {
            return Optional.empty();
        }
        String value = values.getFirst();
        if (value.length() <= BEARER_PREFIX.length()
                || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AccessToken(value.substring(BEARER_PREFIX.length()).trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<ApplicationName> applicationName(ServerRequest request) {
        List<String> values = request.query().all(APPLICATION_PARAM, List::of);
        if (values.size() != 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ApplicationName(values.getFirst()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void unauthorized(ServerResponse response, String errorCode) {
        response.header(HeaderNames.WWW_AUTHENTICATE,
                errorCode == null ? "Bearer" : "Bearer error=\"" + errorCode + "\"");
        error(response, Status.UNAUTHORIZED_401, errorCode == null ? "unauthorized" : errorCode);
    }

    private static void error(ServerResponse response, Status status, String errorCode) {
        response.status(status).send(Json.createObjectBuilder().add("error", errorCode).build());
    }

    private static jakarta.json.JsonObject toJson(Authorization authorization) {
        JsonArrayBuilder roles = Json.createArrayBuilder();
        authorization.roles().stream().map(Role::name).sorted().forEach(roles::add);
        return Json.createObjectBuilder()
                .add("username", authorization.username().value())
                .add("application", authorization.application().value())
                .add("roles", roles)
                .build();
    }
}
