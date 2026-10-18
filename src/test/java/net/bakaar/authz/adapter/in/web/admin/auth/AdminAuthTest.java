package net.bakaar.authz.adapter.in.web.admin.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static net.bakaar.authz.support.AdminClient.LOGIN_COOKIE;
import static net.bakaar.authz.support.AdminClient.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.restassured.response.Response;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bakaar.authz.Main;
import net.bakaar.authz.support.AdminClient;
import net.bakaar.authz.support.AdminClient.AdminCookies;
import net.bakaar.authz.support.AdminClient.LoginStart;
import net.bakaar.authz.support.FakeKeycloak;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End to end: real server, real PostgreSQL, {@link FakeKeycloak} as identity provider. The sign-in flow never
 * touches the database, but the users tab it lands on does.
 */
class AdminAuthTest {

    private static TestDatabase database;
    private static FakeKeycloak keycloak;
    private static Main.RunningServer server;

    @BeforeAll
    static void setUp() {
        database = new TestDatabase();
        keycloak = new FakeKeycloak();
        server = Main.start(config(keycloak.baseUrl(), database));
    }

    @AfterAll
    static void tearDown() {
        server.close();
        keycloak.close();
        database.close();
    }

    @Test
    void anonymousPageRequestRedirectsToLogin() {
        given().port(server.port()).redirects().follow(false)
                .when().get("/admin/applications")
                .then().statusCode(302)
                .header("Location", "/admin/login?returnTo=%2Fadmin%2Fapplications")
                .header("Cache-Control", "no-store");
    }

    @Test
    void anonymousHtmxRequestGets401WithHxRedirect() {
        given().port(server.port()).redirects().follow(false)
                .header("HX-Request", "true")
                .when().get("/admin/applications")
                .then().statusCode(401)
                .header("HX-Redirect", "/admin/login?returnTo=%2Fadmin%2Fapplications");
    }

    @Test
    void anonymousWriteIsRedirectedNotExecuted() {
        given().port(server.port()).redirects().follow(false)
                .when().post("/admin/logout")
                .then().statusCode(302)
                .header("Location", startsWith("/admin/login"));
    }

    @Test
    void pathTraversalOutOfTheExemptPathsStillRequiresASession() {
        given().port(server.port()).redirects().follow(false).urlEncodingEnabled(false)
                .when().get("/admin/static/../applications")
                .then().statusCode(302)
                .header("Location", startsWith("/admin/login"));
        given().port(server.port()).redirects().follow(false).urlEncodingEnabled(false)
                .when().get("/admin/static/%2e%2e/applications")
                .then().statusCode(302)
                .header("Location", startsWith("/admin/login"));
    }

    @Test
    void loginRedirectsToKeycloakWithPkceStateAndNonce() {
        Response response = given().port(server.port()).redirects().follow(false)
                .queryParam("returnTo", "/admin/applications")
                .when().get("/admin/login");

        response.then().statusCode(302).header("Cache-Control", "no-store");
        URI location = URI.create(response.header("Location"));
        assertThat(location.toString()).startsWith(keycloak.baseUrl() + "/auth?");
        Map<String, String> query = FakeKeycloak.query(location);
        assertThat(query).containsEntry("response_type", "code")
                .containsEntry("client_id", FakeKeycloak.CLIENT_ID)
                .containsEntry("redirect_uri", FakeKeycloak.REDIRECT_URI)
                .containsEntry("scope", "openid")
                .containsEntry("code_challenge_method", "S256");
        assertThat(query.get("state")).hasSizeGreaterThanOrEqualTo(32);
        assertThat(query.get("nonce")).hasSizeGreaterThanOrEqualTo(32).isNotEqualTo(query.get("state"));
        assertThat(query.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");

        String setCookie = setCookie(response, LOGIN_COOKIE);
        assertThat(setCookie).contains("Path=/oidc", "HttpOnly", "Secure", "SameSite=Lax", "Max-Age=600");
    }

    @Test
    void codeExchangeUsesClientSecretBasicAndThePkceVerifier() {
        LoginStart start = AdminClient.startLogin(server.port(), "/admin/login");
        AdminClient.visitKeycloak(start);
        keycloak.stubNextLogin("carol", "authz-reader");

        Response callback = AdminClient.callback(server.port(), start.loginCookie(), start.state());

        callback.then().statusCode(302).header("Location", "/admin");
        LoggedRequest tokenRequest = keycloak.wireMock().findAll(postRequestedFor(urlPathEqualTo("/token"))).getLast();
        Map<String, String> form = FakeKeycloak.query(URI.create("x:/?" + tokenRequest.getBodyAsString()));
        assertThat(form).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "test-code")
                .containsEntry("redirect_uri", FakeKeycloak.REDIRECT_URI)
                .doesNotContainKey("client_secret");
        String challenge = FakeKeycloak.query(start.authorizationUri()).get("code_challenge");
        assertThat(s256(form.get("code_verifier"))).isEqualTo(challenge);
        assertThat(tokenRequest.getHeader("Authorization")).startsWith("Basic ");

        String sessionCookie = setCookie(callback, SESSION_COOKIE);
        assertThat(sessionCookie).contains("Path=/", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(setCookie(callback, LOGIN_COOKIE)).contains("Path=/oidc", "Expires=Thu, 1 Jan 1970 00:00:00 GMT");
    }

    @Test
    void callbackWithWrongStateIsRejected() {
        LoginStart start = AdminClient.startLogin(server.port(), "/admin/login");
        AdminClient.visitKeycloak(start);
        keycloak.stubNextLogin("carol", "authz-admin");

        AdminClient.callback(server.port(), start.loginCookie(), "forged-state")
                .then().statusCode(400).header("Cache-Control", "no-store");
        AdminClient.callback(server.port(), null, start.state())
                .then().statusCode(400);
        AdminClient.callback(server.port(), "garbage", start.state())
                .then().statusCode(400);
    }

    @Test
    void returnToOutsideAdminFallsBackToAdmin() {
        for (String returnTo : List.of("https://evil.example", "//evil.example", "/admin/../../evil", "/administrator")) {
            LoginStart start = AdminClient.startLogin(server.port(),
                    "/admin/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8));
            AdminClient.visitKeycloak(start);
            keycloak.stubNextLogin("carol", "authz-admin");

            AdminClient.callback(server.port(), start.loginCookie(), start.state())
                    .then().statusCode(302).header("Location", "/admin");
        }
    }

    @Test
    void returnToUnderAdminIsKept() {
        LoginStart start = AdminClient.startLogin(server.port(), "/admin/login?returnTo=%2Fadmin%2Fapplications");
        AdminClient.visitKeycloak(start);
        keycloak.stubNextLogin("carol", "authz-admin");

        AdminClient.callback(server.port(), start.loginCookie(), start.state())
                .then().statusCode(302).header("Location", "/admin/applications");
    }

    @Test
    void readerCanReadTheUsersTab() {
        AdminCookies reader = AdminClient.login(server.port(), keycloak, "rita", "authz-reader");

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, reader.session())
                .when().get("/admin")
                .then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body(containsString("<meta name=\"csrf-token\" content=\"" + reader.csrf() + "\">"));
    }

    @Test
    void logoutWithoutCsrfTokenGets403() {
        AdminCookies admin = AdminClient.login(server.port(), keycloak, "ada", "authz-admin");

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, admin.session())
                .when().post("/admin/logout")
                .then().statusCode(403);
        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, admin.session())
                .header("X-CSRF-Token", admin.csrf() + "x")
                .when().post("/admin/logout")
                .then().statusCode(403);
    }

    @Test
    void csrfTokenOfAnotherSessionGets403() {
        AdminCookies ada = AdminClient.login(server.port(), keycloak, "ada", "authz-admin");
        AdminCookies bob = AdminClient.login(server.port(), keycloak, "bob", "authz-admin");

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, ada.session())
                .header("X-CSRF-Token", bob.csrf())
                .when().post("/admin/logout")
                .then().statusCode(403);
    }

    @Test
    void readerCanLogOut() {
        AdminCookies reader = AdminClient.login(server.port(), keycloak, "rita", "authz-reader");

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, reader.session())
                .header("X-CSRF-Token", reader.csrf())
                .when().post("/admin/logout")
                .then().statusCode(204);
    }

    @Test
    void userWithoutAdminRolesGets403() {
        String session = AdminClient.sessionCookie(server.port(), keycloak, "alice", "some-other-role");

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .header("HX-Request", "true")
                .when().get("/admin/applications")
                .then().statusCode(403)
                .header("Cache-Control", "no-store");
        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .header("X-CSRF-Token", forbiddenPageCsrf(session))
                .when().post("/admin/users")
                .then().statusCode(403);
    }

    @Test
    void userWithoutAdminRolesSeesAForbiddenPageWithSignOut() {
        String session = AdminClient.sessionCookie(server.port(), keycloak, "alice", "some-other-role");

        Response page = given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .when().get("/admin");

        page.then().statusCode(403)
                .header("Cache-Control", "no-store")
                .header("Content-Type", startsWith("text/html"))
                .header("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'")
                .body(containsString("You do not have access to the admin UI"))
                .body(containsString("alice"))
                .body(containsString("hx-post=\"/admin/logout\""))
                .body(containsString("<link rel=\"stylesheet\" href=\"/admin/static/admin.css\">"));
        assertThat(page.asString()).doesNotContain("<script>", "style=", " onclick=");
        String csrf = forbiddenPageCsrf(session);
        assertThat(page.asString()).contains("hx-headers='{\"X-CSRF-Token\":\"" + csrf + "\"}'");
    }

    @Test
    void userWithoutAdminRolesCanLogOut() {
        String session = AdminClient.sessionCookie(server.port(), keycloak, "alice", "some-other-role");
        String csrf = forbiddenPageCsrf(session);

        given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .when().post("/admin/logout")
                .then().statusCode(403);
        Response response = given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .header("HX-Request", "true")
                .header("X-CSRF-Token", csrf)
                .when().post("/admin/logout");

        response.then().statusCode(204);
        assertThat(response.header("HX-Redirect")).startsWith(keycloak.baseUrl() + "/logout?");
        assertThat(setCookie(response, SESSION_COOKIE)).startsWith(SESSION_COOKIE + "=;");
    }

    @Test
    void idTokenWithWrongNonceOrIssuerIsRejected() {
        assertCallbackRejected(() -> keycloak.stubIdToken(
                keycloak.idTokenClaims("ada", "authz-admin").claim("nonce", "other-nonce").build()));
        assertCallbackRejected(() -> keycloak.stubIdToken(
                keycloak.idTokenClaims("ada", "authz-admin").issuer("https://evil.example").build()));
        assertCallbackRejected(() -> keycloak.stubIdToken(
                keycloak.idTokenClaims("ada", "authz-admin").audience("another-client").build()));
        assertCallbackRejected(() -> keycloak.stubIdToken(
                keycloak.idTokenClaims("ada", "authz-admin").expirationTime(null).build()));
        assertCallbackRejected(() -> keycloak.stubIdToken(
                keycloak.idTokenClaims("ada", "authz-admin").claim("preferred_username", " ").build()));
    }

    @Test
    void logoutClearsSessionAndRedirectsToKeycloak() {
        LoginStart start = AdminClient.startLogin(server.port(), "/admin/login");
        AdminClient.visitKeycloak(start);
        String idToken = keycloak.stubNextLogin("ada", "authz-admin");
        String session = AdminClient.callback(server.port(), start.loginCookie(), start.state())
                .then().statusCode(302).extract().cookie(SESSION_COOKIE);
        String csrf = AdminClient.csrfToken(server.port(), session);
        // the session carries the ID token for the logout hint; a Keycloak one is about 1 KB, ours is similar
        assertThat(session).hasSizeLessThan(3000);

        Response response = given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .header("HX-Request", "true")
                .header("X-CSRF-Token", csrf)
                .when().post("/admin/logout");

        response.then().statusCode(204).header("Cache-Control", "no-store");
        assertThat(setCookie(response, SESSION_COOKIE)).startsWith(SESSION_COOKIE + "=;")
                .contains("Path=/", "Expires=Thu, 1 Jan 1970 00:00:00 GMT");
        URI hxRedirect = URI.create(response.header("HX-Redirect"));
        assertThat(hxRedirect.toString()).startsWith(keycloak.baseUrl() + "/logout?");
        assertThat(FakeKeycloak.query(hxRedirect))
                .containsEntry("client_id", FakeKeycloak.CLIENT_ID)
                .containsEntry("post_logout_redirect_uri", "http://localhost:8080/admin")
                // with the hint, Keycloak ends the SSO session without asking "Do you want to log out?"
                .containsEntry("id_token_hint", idToken);
    }

    @Test
    void apiAndHealthAreNotFilteredByTheAdminSession() {
        given().port(server.port()).redirects().follow(false)
                .when().get("/api/v1/authorizations?application=billing")
                .then().statusCode(401)
                .header("WWW-Authenticate", "Bearer");
        given().port(server.port()).when().get("/observe/health").then().statusCode(204);
    }

    @Test
    void serverStartsWithoutReachableKeycloak() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        try (Main.RunningServer offline = Main.start(config("http://localhost:" + closedPort, null))) {
            given().port(offline.port()).redirects().follow(false)
                    .when().get("/admin")
                    .then().statusCode(302)
                    .header("Location", equalTo("/admin/login?returnTo=%2Fadmin"));
            given().port(offline.port()).redirects().follow(false)
                    .when().get("/admin/login")
                    .then().statusCode(503);
        }
    }

    private void assertCallbackRejected(Runnable stubToken) {
        LoginStart start = AdminClient.startLogin(server.port(), "/admin/login");
        AdminClient.visitKeycloak(start);
        stubToken.run();

        Response callback = AdminClient.callback(server.port(), start.loginCookie(), start.state());

        callback.then().statusCode(400);
        assertThat(callback.getHeaders().getValues("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith(SESSION_COOKIE + "="));
    }

    private String forbiddenPageCsrf(String session) {
        String page = given().port(server.port()).redirects().follow(false)
                .cookie(SESSION_COOKIE, session)
                .when().get("/admin")
                .then().statusCode(403)
                .extract().asString();
        Matcher matcher = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\">").matcher(page);
        assertThat(matcher.find()).as("csrf-token meta on the 403 page").isTrue();
        return matcher.group(1);
    }

    private static String setCookie(Response response, String name) {
        return response.getHeaders().getValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie for " + name));
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Without a database, the server starts against an unreachable one, as it does during the AOT training run.
     */
    private static Config config(String keycloakBaseUrl, TestDatabase database) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Map<String, String> values = new HashMap<>(database == null
                ? Map.of("db.url", "jdbc:postgresql://localhost:1/unreachable",
                        "db.username", "nobody",
                        "db.password", "nobody",
                        "db.migrate", "false")
                : Map.of("db.url", database.jdbcUrl(),
                        "db.username", database.username(),
                        "db.password", database.password()));
        values.putAll(Map.of(
                "server.port", "0",
                "jwt.issuer", FakeKeycloak.ISSUER,
                "jwt.jwks-uri", keycloakBaseUrl + "/jwks",
                "jwt.audiences", "authz-server",
                "jwt.allow-insecure-jwks-uri", "true"));
        values.putAll(Map.of(
                "admin.oidc.issuer", FakeKeycloak.ISSUER,
                "admin.oidc.discovery-base-url", keycloakBaseUrl,
                "admin.oidc.client-id", FakeKeycloak.CLIENT_ID,
                "admin.oidc.client-secret", FakeKeycloak.CLIENT_SECRET,
                "admin.oidc.redirect-uri", FakeKeycloak.REDIRECT_URI,
                "admin.oidc.allow-insecure", "true",
                "admin.session-key", Base64.getEncoder().encodeToString(key)));
        return Config.just(ConfigSources.create(values));
    }
}
