package net.bakaar.authz.adapter.in.web.admin.auth;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bakaar.authz.adapter.in.web.admin.auth.OidcClient.OidcException;

/**
 * Sign-in and sign-out of the admin UI. Register at the routing root: it serves {@code GET /admin/login},
 * {@code GET /oidc/redirect} and {@code POST /admin/logout} (the last one behind {@link AdminAuthFilter}).
 */
public final class LoginRoutes implements HttpService {

    private static final Logger LOGGER = Logger.getLogger(LoginRoutes.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HOME = "/admin";

    private final OidcClient oidc;
    private final OidcSettings settings;
    private final SessionCookies cookies;
    private final Duration sessionDuration;
    private final AuthPages pages;

    public LoginRoutes(OidcClient oidc, OidcSettings settings, SessionCookies cookies, Duration sessionDuration,
                       AuthPages pages) {
        this.oidc = oidc;
        this.settings = settings;
        this.cookies = cookies;
        this.sessionDuration = sessionDuration;
        this.pages = pages;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/admin/login", this::startLogin)
                .get("/oidc/redirect", this::completeLogin)
                .post(AdminAuthFilter.LOGOUT_PATH, this::logout);
    }

    private void startLogin(ServerRequest request, ServerResponse response) {
        response.header(HeaderNames.CACHE_CONTROL, "no-store");
        LoginState login = new LoginState(randomToken(), randomToken(), randomToken(),
                safeReturnTo(request.query().first("returnTo").asOptional()),
                Instant.now().plus(SessionCookies.LOGIN_DURATION));
        URI authorization;
        try {
            authorization = oidc.authorizationUri(login);
        } catch (OidcException e) {
            LOGGER.log(Level.WARNING, () -> "Admin sign-in unavailable: " + e.getMessage());
            response.status(Status.SERVICE_UNAVAILABLE_503).send();
            return;
        }
        cookies.setLoginState(response, login);
        response.header(HeaderNames.LOCATION, authorization.toString()).status(Status.FOUND_302).send();
    }

    private void completeLogin(ServerRequest request, ServerResponse response) {
        response.header(HeaderNames.CACHE_CONTROL, "no-store");
        Optional<LoginState> found = cookies.loginState(request);
        cookies.clearLoginState(response);
        if (request.query().contains("error")) {
            // RFC 6749 4.1.2.1: the user cancelled or Keycloak refused; the error code is not reflected
            LOGGER.fine("Admin sign-in callback carries an error instead of a code");
            pages.signInCancelled(request, response);
            return;
        }
        List<String> states = request.query().all("state", List::of);
        List<String> codes = request.query().all("code", List::of);
        if (found.isEmpty() || states.size() != 1 || codes.size() != 1
                || !constantTimeEquals(states.getFirst(), found.get().state())) {
            LOGGER.fine("Admin sign-in callback rejected: missing or mismatching state or login cookie");
            response.status(Status.BAD_REQUEST_400).send();
            return;
        }
        LoginState login = found.get();
        AdminIdentity identity;
        try {
            identity = oidc.exchange(codes.getFirst(), login);
        } catch (OidcException e) {
            LOGGER.log(Level.WARNING, () -> "Admin sign-in failed: " + e.getMessage());
            response.status(Status.BAD_REQUEST_400).send();
            return;
        }
        cookies.setSession(response, new AdminSession(identity.username(), identity.roles(), randomToken(),
                identity.idToken(), Instant.now().plus(sessionDuration)));
        LOGGER.info(() -> "Admin sign-in: " + identity.username() + " " + identity.roles());
        response.header(HeaderNames.LOCATION, login.returnTo()).status(Status.FOUND_302).send();
    }

    /**
     * RP-initiated logout: with {@code id_token_hint}, Keycloak ends the SSO session at once instead of asking
     * "Do you want to log out?", so {@code /admin} cannot silently sign the user back in.
     */
    private void logout(ServerRequest request, ServerResponse response) {
        response.header(HeaderNames.CACHE_CONTROL, "no-store");
        AdminSession session = AdminAuthFilter.session(request);
        cookies.clearSession(response);
        String target;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("client_id", settings.clientId());
            params.put("post_logout_redirect_uri", settings.origin() + HOME);
            params.put("id_token_hint", session.idToken());
            target = OidcClient.withQuery(oidc.endSessionUri(), params).toString();
        } catch (OidcException e) {
            LOGGER.log(Level.WARNING, () -> "Keycloak logout skipped: " + e.getMessage());
            target = HOME;
        }
        response.header(AdminAuthFilter.HX_REDIRECT, target).status(Status.NO_CONTENT_204).send();
    }

    /**
     * Only local paths under {@code /admin} are kept; anything else (other origins, {@code //host}, traversal,
     * any percent-encoding such as {@code %2e%2e}, control characters) becomes {@code /admin}, so the callback can
     * never redirect off the admin UI.
     */
    static String safeReturnTo(Optional<String> returnTo) {
        return returnTo
                .filter(path -> path.equals(HOME) || path.startsWith(HOME + "/"))
                .filter(path -> !path.contains("//") && !path.contains("\\") && !path.contains("/..")
                        && !path.contains("%"))
                .filter(path -> path.chars().allMatch(c -> c > 0x20 && c < 0x7f))
                .orElse(HOME);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
