package net.bakaar.authz.adapter.in.web.admin;

import static io.restassured.RestAssured.given;
import static net.bakaar.authz.support.AdminClient.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.bakaar.authz.Main;
import net.bakaar.authz.common.Ids;
import net.bakaar.authz.support.AdminClient;
import net.bakaar.authz.support.AdminClient.AdminCookies;
import net.bakaar.authz.support.FakeKeycloak;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End to end: real server, real PostgreSQL, {@link FakeKeycloak} as identity provider.
 */
class UsersTabTest {

    private static TestDatabase database;
    private static FakeKeycloak keycloak;
    private static Main.RunningServer server;
    private static AdminCookies admin;
    private static AdminCookies reader;

    @BeforeAll
    static void setUp() throws Exception {
        database = new TestDatabase();
        keycloak = new FakeKeycloak();
        server = Main.start(config());
        database.insertFixtures();
        insertUserWithRole("<b>x</b>", database.idOf("role", "crm/viewer"));
        admin = AdminClient.login(server.port(), keycloak, "ada", "authz-admin");
        reader = AdminClient.login(server.port(), keycloak, "rita", "authz-reader");
    }

    @AfterAll
    static void tearDown() {
        server.close();
        keycloak.close();
        database.close();
    }

    @Test
    void fullPageHasLayoutTabsAndUsers() {
        String body = as(reader).when().get("/admin")
                .then().statusCode(200)
                .contentType("text/html; charset=UTF-8")
                .extract().asString();

        assertThat(body).contains("<html", "id=\"tab-content\"", "id=\"dialog-slot\"",
                "hx-get=\"/admin\"", "hx-get=\"/admin/applications\"",
                "alice", "bob", "billing", "crm", "viewer",
                "<meta name=\"csrf-token\" content=\"" + reader.csrf() + "\">",
                "hx-headers='{\"X-CSRF-Token\":\"" + reader.csrf() + "\"}'",
                "/admin/static/admin.css", "/admin/static/admin.js", "/admin/static/htmx.min.js",
                "\"historyCacheSize\":0", "\"refreshOnHistoryMiss\":true")
                .doesNotContain("hx-swap-oob");
    }

    @Test
    void fullPageHasNoInlineScriptsOrStyles() {
        String body = as(admin).when().get("/admin").then().statusCode(200).extract().asString();

        assertThat(body).doesNotContain("<style", " style=", " onclick=");
        assertThat(Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>").matcher(body).find())
                .as("inline <script> without src").isFalse();
    }

    @Test
    void htmxRequestGetsOnlyTabContent() {
        String body = as(reader).header("HX-Request", "true")
                .when().get("/admin")
                .then().statusCode(200)
                .extract().asString();

        assertThat(body).doesNotContain("<html", "id=\"dialog-slot\"")
                .contains("id=\"tab-content\"", "alice", "hx-swap-oob=\"true\"");
    }

    @Test
    void historyRestoreRequestGetsTheFullPage() {
        String body = as(reader).header("HX-Request", "true").header("HX-History-Restore-Request", "true")
                .when().get("/admin")
                .then().statusCode(200)
                .extract().asString();

        assertThat(body).contains("<html", "alice");
    }

    @Test
    void readerSeesNoWriteControls() {
        String body = as(reader).when().get("/admin").then().statusCode(200).extract().asString();

        assertThat(body).contains("alice")
                .doesNotContain("+ New user", "+ Grant", "hx-delete", "/grants/new", "/delete\"", "class=\"chip-x\"");
    }

    @Test
    void adminSeesWriteControls() {
        String body = as(admin).when().get("/admin").then().statusCode(200).extract().asString();
        long alice = database.idOf("app_user", "alice");
        long billingViewer = database.idOf("role", "billing/viewer");

        assertThat(body).contains("+ New user", "+ Grant",
                "hx-get=\"/admin/users/new\"",
                "hx-get=\"/admin/users/" + alice + "/grants/new\"",
                "hx-get=\"/admin/users/" + alice + "/grants/" + billingViewer + "/delete\"");
    }

    @Test
    void escapesUserProvidedText() {
        String body = as(reader).when().get("/admin").then().statusCode(200).extract().asString();

        assertThat(body).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }

    @Test
    void sendsSecurityHeaders() {
        as(reader).when().get("/admin")
                .then().statusCode(200)
                .header("Cache-Control", "no-store")
                .header("Content-Security-Policy", equalTo(AdminPages.CONTENT_SECURITY_POLICY));
    }

    @Test
    void staticAssetsAreServedWithoutSession() {
        for (String asset : new String[] {"admin.css", "admin.js", "htmx.min.js"}) {
            given().port(server.port()).redirects().follow(false)
                    .when().get("/admin/static/" + asset)
                    .then().statusCode(200);
        }
        given().port(server.port()).when().get("/admin/static/admin.css")
                .then().statusCode(200).contentType(startsWith("text/css"));
    }

    @Test
    void staticPathTraversalDoesNotServeTheConfiguration() {
        for (String path : new String[] {"/admin/static/../application.yaml", "/admin/static/%2e%2e/application.yaml"}) {
            Response response = as(admin).urlEncodingEnabled(false).when().get(path);

            assertThat(response.statusCode() != 200 || !response.asString().contains("jwt:"))
                    .as("%s answered %d with the configuration", path, response.statusCode()).isTrue();
        }
    }

    /**
     * Helidon answers a {@code RequestException} itself (status of the exception, empty body) without calling
     * routing error handlers, so the catch-all 500 never sees it.
     */
    @Test
    void malformedRequestIs400NotA500() throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /admin?x=%zz HTTP/1.1\r\nHost: localhost\r\nCookie: " + SESSION_COOKIE + "="
                    + admin.session() + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);

            assertThat(response).startsWith("HTTP/1.1 400").contains("Content-Length: 0");
        }
    }

    @Test
    void unknownAdminRouteIs404WithEmptyBody() {
        as(admin).when().get("/admin/nope")
                .then().statusCode(404)
                .body(emptyString());
    }

    @Test
    void usesOneOfEightApplicationColors() {
        String body = as(reader).when().get("/admin").then().statusCode(200).extract().asString();
        long billing = database.idOf("application", "billing");

        assertThat(body).contains("app-" + Long.remainderUnsigned(billing, 8));
    }

    private static RequestSpecification as(AdminCookies cookies) {
        return given().port(server.port()).redirects().follow(false).cookie(SESSION_COOKIE, cookies.session());
    }

    private static void insertUserWithRole(String username, long roleId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password())) {
            long userId = Ids.next();
            try (PreparedStatement user = connection.prepareStatement(
                    "INSERT INTO app_user (id, username) VALUES (?, ?)")) {
                user.setLong(1, userId);
                user.setString(2, username);
                user.executeUpdate();
            }
            try (PreparedStatement grant = connection.prepareStatement(
                    "INSERT INTO user_role (user_id, role_id) VALUES (?, ?)")) {
                grant.setLong(1, userId);
                grant.setLong(2, roleId);
                grant.executeUpdate();
            }
        }
    }

    private static Config config() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Map<String, String> values = new HashMap<>(Map.of(
                "server.port", "0",
                "db.url", database.jdbcUrl(),
                "db.username", database.username(),
                "db.password", database.password(),
                "jwt.issuer", FakeKeycloak.ISSUER,
                "jwt.jwks-uri", keycloak.baseUrl() + "/jwks",
                "jwt.audiences", "authz-server",
                "jwt.allow-insecure-jwks-uri", "true"));
        values.putAll(Map.of(
                "admin.oidc.issuer", FakeKeycloak.ISSUER,
                "admin.oidc.discovery-base-url", keycloak.baseUrl(),
                "admin.oidc.client-id", FakeKeycloak.CLIENT_ID,
                "admin.oidc.client-secret", FakeKeycloak.CLIENT_SECRET,
                "admin.oidc.redirect-uri", FakeKeycloak.REDIRECT_URI,
                "admin.oidc.allow-insecure", "true",
                "admin.session-key", Base64.getEncoder().encodeToString(key)));
        return Config.just(ConfigSources.create(values));
    }
}
