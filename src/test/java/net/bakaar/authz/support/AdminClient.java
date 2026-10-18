package net.bakaar.authz.support;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs in to the admin UI through the real code flow, with {@link FakeKeycloak} as the identity provider.
 */
public final class AdminClient {

    public static final String SESSION_COOKIE = "AUTHZ_ADMIN_SESSION";
    public static final String LOGIN_COOKIE = "AUTHZ_ADMIN_LOGIN";

    private static final Pattern CSRF_META = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\">");

    private AdminClient() {
    }

    public record AdminCookies(String session, String csrf) {
    }

    /**
     * The login cookie set by {@code /admin/login} and the authorization endpoint URI it redirected to.
     */
    public record LoginStart(String loginCookie, URI authorizationUri) {

        public String state() {
            return FakeKeycloak.query(authorizationUri).get("state");
        }
    }

    /**
     * Full sign-in: {@code GET /admin}, the redirect to Keycloak, the callback, then reads the CSRF token from the page.
     */
    public static AdminCookies login(int serverPort, FakeKeycloak keycloak, String username, String... roles) {
        String session = sessionCookie(serverPort, keycloak, username, roles);
        return new AdminCookies(session, csrfToken(serverPort, session));
    }

    /**
     * Runs the code flow and returns only the session cookie (works for users the UI then refuses).
     */
    public static String sessionCookie(int serverPort, FakeKeycloak keycloak, String username, String... roles) {
        String location = given().port(serverPort).redirects().follow(false)
                .when().get("/admin")
                .then().statusCode(302)
                .extract().header("Location");
        LoginStart start = startLogin(serverPort, location);
        visitKeycloak(start);
        keycloak.stubNextLogin(username, roles);
        Response callback = callback(serverPort, start.loginCookie(), start.state());
        if (callback.statusCode() != 302) {
            throw new IllegalStateException("Sign-in callback failed with " + callback.statusCode());
        }
        return callback.getCookie(SESSION_COOKIE);
    }

    /**
     * Follows a path such as {@code /admin/login?returnTo=...} and captures the login cookie and Keycloak redirect.
     */
    public static LoginStart startLogin(int serverPort, String loginPathAndQuery) {
        Response response = given().port(serverPort).redirects().follow(false).urlEncodingEnabled(false)
                .when().get(loginPathAndQuery);
        if (response.statusCode() != 302) {
            throw new IllegalStateException("/admin/login answered " + response.statusCode());
        }
        return new LoginStart(response.getCookie(LOGIN_COOKIE), URI.create(response.header("Location")));
    }

    /**
     * Sends the browser to the authorization endpoint, so {@link FakeKeycloak} sees the state and nonce.
     */
    public static void visitKeycloak(LoginStart start) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(HttpRequest.newBuilder(start.authorizationUri()).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach the fake Keycloak", e);
        }
    }

    public static Response callback(int serverPort, String loginCookie, String state) {
        var request = given().port(serverPort).redirects().follow(false)
                .queryParam("code", "test-code")
                .queryParam("state", state);
        if (loginCookie != null) {
            request.cookie(LOGIN_COOKIE, loginCookie);
        }
        return request.when().get("/oidc/redirect");
    }

    public static String csrfToken(int serverPort, String session) {
        String page = given().port(serverPort).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .when().get("/admin")
                .then().statusCode(200)
                .extract().asString();
        Matcher matcher = CSRF_META.matcher(page);
        if (!matcher.find()) {
            throw new IllegalStateException("No csrf-token meta tag on /admin");
        }
        return matcher.group(1);
    }
}
