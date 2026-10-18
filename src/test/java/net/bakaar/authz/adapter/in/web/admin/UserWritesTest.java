package net.bakaar.authz.adapter.in.web.admin;

import static io.restassured.RestAssured.given;
import static net.bakaar.authz.support.AdminClient.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.emptyString;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.bakaar.authz.Main;
import net.bakaar.authz.support.AdminClient;
import net.bakaar.authz.support.AdminClient.AdminCookies;
import net.bakaar.authz.support.FakeKeycloak;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * User dialogs and writes, end to end: real server, real PostgreSQL, {@link FakeKeycloak} as identity provider.
 * The fixtures are reset before every test.
 */
class UserWritesTest {

    private static final String CHANGED_TRIGGER = "{\"authz:changed\":true,\"authz:close-dialog\":true}";
    private static final String FORM = "application/x-www-form-urlencoded; charset=UTF-8";

    private static TestDatabase database;
    private static FakeKeycloak keycloak;
    private static Main.RunningServer server;
    private static AdminCookies admin;
    private static AdminCookies reader;

    @BeforeAll
    static void setUp() {
        database = new TestDatabase();
        keycloak = new FakeKeycloak();
        server = Main.start(config());
        database.insertFixtures();
        admin = AdminClient.login(server.port(), keycloak, "ada", "authz-admin");
        reader = AdminClient.login(server.port(), keycloak, "rita", "authz-reader");
    }

    @BeforeEach
    void resetFixtures() {
        database.resetFixtures();
    }

    @AfterAll
    static void tearDown() {
        server.close();
        keycloak.close();
        database.close();
    }

    // ------------------------------------------------------------------ dialogs

    @Test
    void newUserDialogListsApplicationsAndPostsToUsers() {
        String body = as(admin).when().get("/admin/users/new").then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "New user", "hx-post=\"/admin/users\"", "name=\"username\"",
                "name=\"applicationId\"",
                "value=\"" + database.idOf("application", "billing") + "\"",
                "value=\"" + database.idOf("application", "crm") + "\"");
        assertNoInlineCode(body);
    }

    @Test
    void userDialogLetsAdminsRenameAndDelete() {
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).when().get("/admin/users/" + bob).then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "value=\"bob\"", "hx-put=\"/admin/users/" + bob + "\"",
                "hx-get=\"/admin/users/" + bob + "/delete\"", "crm", "admin");
        assertNoInlineCode(body);
    }

    @Test
    void userDialogIsReadOnlyForReaders() {
        long bob = database.idOf("app_user", "bob");

        String body = as(reader).when().get("/admin/users/" + bob).then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "bob", "crm", "admin")
                .doesNotContain("hx-put", "hx-delete", "/delete\"", "<input");
    }

    @Test
    void deleteUserConfirmationTargetsTheUser() {
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).when().get("/admin/users/" + bob + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "bob", "hx-delete=\"/admin/users/" + bob + "\"");
        assertNoInlineCode(body);
    }

    @Test
    void grantDialogFixesTheUsername() {
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).when().get("/admin/users/" + bob + "/grants/new")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "bob", "hx-post=\"/admin/users/" + bob + "/grants\"",
                "name=\"applicationId\"")
                .doesNotContain("name=\"username\"");
    }

    @Test
    void roleOptionsListOnlyThatApplicationsRoles() {
        long billing = database.idOf("application", "billing");

        String body = as(reader).when().get("/admin/applications/" + billing + "/role-options")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("admin", "viewer", "name=\"roleId\"",
                        "value=\"" + database.idOf("role", "billing/admin") + "\"",
                        "value=\"" + database.idOf("role", "billing/viewer") + "\"")
                .doesNotContain(String.valueOf(database.idOf("role", "crm/admin")),
                        String.valueOf(database.idOf("role", "crm/viewer")));
    }

    @Test
    void dialogButtonsInTheTabDoNotInheritItsOuterHtmlSwap() {
        String body = as(admin).when().get("/admin").then().statusCode(200).extract().asString();

        // otherwise "+ New user" would replace #dialog-slot itself instead of filling it
        assertThat(body).contains("hx-swap=\"outerHTML\" hx-disinherit=\"*\"");
    }

    // ------------------------------------------------------------------ create

    @Test
    void createUserWithRoleReturns204AndTrigger() {
        as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "  dave  ")
                .formParam("applicationId", database.idOf("application", "billing"))
                .formParam("roleId", database.idOf("role", "billing/viewer"))
                .when().post("/admin/users")
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER)
                .body(emptyString());

        assertThat(as(admin).when().get("/admin").then().statusCode(200).extract().asString())
                .contains(">dave<");
        assertThat(database.idOf("app_user", "dave")).as("username stored trimmed").isPositive();
    }

    @Test
    void createUserWithoutRoleIs422AndShowsError() {
        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "dave")
                .formParam("applicationId", database.idOf("application", "billing"))
                .when().post("/admin/users")
                .then().statusCode(422)
                .extract().asString();

        assertThat(body).contains("<dialog", "Select at least one role", "value=\"dave\"");
        assertThatThrownBy(() -> database.idOf("app_user", "dave")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createUserWithForeignRoleIs422() {
        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "dave")
                .formParam("applicationId", database.idOf("application", "billing"))
                .formParam("roleId", database.idOf("role", "crm/admin"))
                .when().post("/admin/users")
                .then().statusCode(422)
                .extract().asString();

        assertThat(body).contains("<dialog", "Roles must belong to the selected application");
        assertThatThrownBy(() -> database.idOf("app_user", "dave")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createUserWithBlankUsernameOrNoApplicationIs422() {
        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "   ")
                .formParam("roleId", database.idOf("role", "billing/viewer"))
                .when().post("/admin/users")
                .then().statusCode(422)
                .extract().asString();

        assertThat(body).contains("<dialog", "Enter a username", "Select an application");
    }

    @Test
    void createUserKeepsTheTickedRolesWhenRerendered() {
        long billingViewer = database.idOf("role", "billing/viewer");

        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "alice")
                .formParam("applicationId", database.idOf("application", "billing"))
                .formParam("roleId", billingViewer)
                .when().post("/admin/users")
                .then().statusCode(409)
                .extract().asString();

        assertThat(Pattern.compile("value=\"" + billingViewer + "\"[^>]*checked").matcher(body).find())
                .as("billing/viewer still ticked").isTrue();
    }

    @Test
    void createExistingUsernameIs409() {
        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "alice")
                .formParam("applicationId", database.idOf("application", "billing"))
                .formParam("roleId", database.idOf("role", "billing/viewer"))
                .when().post("/admin/users")
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "already taken", "value=\"alice\"");
    }

    // ------------------------------------------------------------------ grant

    @Test
    void grantAddsRolesToExistingUser() {
        long bob = database.idOf("app_user", "bob");

        as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("applicationId", database.idOf("application", "billing"))
                .formParam("roleId", database.idOf("role", "billing/admin"), database.idOf("role", "billing/viewer"))
                .when().post("/admin/users/" + bob + "/grants")
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);

        String dialog = as(admin).when().get("/admin/users/" + bob).then().statusCode(200).extract().asString();
        assertThat(dialog).contains("billing", "viewer", "crm");
    }

    @Test
    void grantWithoutRoleIs422() {
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("applicationId", database.idOf("application", "billing"))
                .when().post("/admin/users/" + bob + "/grants")
                .then().statusCode(422)
                .extract().asString();

        assertThat(body).contains("<dialog", "Select at least one role", "hx-post=\"/admin/users/" + bob + "/grants\"");
    }

    // ------------------------------------------------------------------ revoke

    @Test
    void revokeConfirmationWarnsWhenLastRole() {
        long bob = database.idOf("app_user", "bob");
        long crmAdmin = database.idOf("role", "crm/admin");

        String body = as(admin).when().get("/admin/users/" + bob + "/grants/" + crmAdmin + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "Remove role?",
                "bob has no other role. Removing crm / admin also deletes the user bob.",
                "Remove and delete bob", "confirmedUserIds", String.valueOf(bob),
                "hx-delete=\"/admin/users/" + bob + "/grants/" + crmAdmin + "\"");
        assertNoInlineCode(body);
    }

    @Test
    void revokeConfirmationIsPlainWhenOtherRolesRemain() {
        long alice = database.idOf("app_user", "alice");
        long billingViewer = database.idOf("role", "billing/viewer");

        String body = as(admin).when().get("/admin/users/" + alice + "/grants/" + billingViewer + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "Remove role?", "billing / viewer", "alice")
                .doesNotContain("deletes the user", "confirmedUserIds", "class=\"warn\"");
    }

    @Test
    void revokeLastRoleWithConfirmationDeletesUser() {
        long bob = database.idOf("app_user", "bob");
        long crmAdmin = database.idOf("role", "crm/admin");

        as(admin).header("X-CSRF-Token", admin.csrf())
                .queryParam("confirmedUserIds", bob)
                .when().delete("/admin/users/" + bob + "/grants/" + crmAdmin)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);

        assertThatThrownBy(() -> database.idOf("app_user", "bob")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokeAcceptsConfirmationInAFormBody() {
        long bob = database.idOf("app_user", "bob");
        long crmAdmin = database.idOf("role", "crm/admin");

        as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("confirmedUserIds", bob)
                .when().delete("/admin/users/" + bob + "/grants/" + crmAdmin)
                .then().statusCode(204);

        assertThatThrownBy(() -> database.idOf("app_user", "bob")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokeLastRoleWithoutConfirmationIs409WithFreshDialog() {
        long bob = database.idOf("app_user", "bob");
        long crmAdmin = database.idOf("role", "crm/admin");

        String body = as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/users/" + bob + "/grants/" + crmAdmin)
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "also deletes the user bob", "confirmedUserIds", String.valueOf(bob));
        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
    }

    @Test
    void revokeOtherRoleRemovesOnlyThatGrant() {
        long alice = database.idOf("app_user", "alice");
        long billingViewer = database.idOf("role", "billing/viewer");

        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/users/" + alice + "/grants/" + billingViewer)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);

        // the grant is gone: its confirmation no longer exists
        as(admin).when().get("/admin/users/" + alice + "/grants/" + billingViewer + "/delete")
                .then().statusCode(404);
        assertThat(database.idOf("app_user", "alice")).isEqualTo(alice);
    }

    @Test
    void staleConfirmationOfANonLastRoleIs409WithPlainConfirmation() {
        long alice = database.idOf("app_user", "alice");
        long billingViewer = database.idOf("role", "billing/viewer");

        String body = as(admin).header("X-CSRF-Token", admin.csrf())
                .queryParam("confirmedUserIds", alice)
                .when().delete("/admin/users/" + alice + "/grants/" + billingViewer)
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "Remove role?", "billing / viewer")
                .doesNotContain("deletes the user", "confirmedUserIds", "class=\"warn\"");
        assertThat(database.idOf("app_user", "alice")).isEqualTo(alice);
    }

    // ------------------------------------------------------------------ rename and delete

    @Test
    void renameAndDeleteUser() {
        long bob = database.idOf("app_user", "bob");

        as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", " robert ")
                .when().put("/admin/users/" + bob)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);
        assertThat(database.idOf("app_user", "robert")).isEqualTo(bob);

        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/users/" + bob)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);
        as(admin).when().get("/admin/users/" + bob).then().statusCode(404);
    }

    @Test
    void renameToTakenOrBlankUsernameRerendersTheDialog() {
        long bob = database.idOf("app_user", "bob");

        String conflict = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "alice")
                .when().put("/admin/users/" + bob)
                .then().statusCode(409).extract().asString();
        String blank = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "  ")
                .when().put("/admin/users/" + bob)
                .then().statusCode(422).extract().asString();

        assertThat(conflict).contains("<dialog", "already taken", "value=\"alice\"");
        assertThat(blank).contains("<dialog", "Enter a username");
        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
    }

    // ------------------------------------------------------------------ errors and access

    @Test
    void unknownIdsAre404() {
        long bob = database.idOf("app_user", "bob");
        long billingViewer = database.idOf("role", "billing/viewer");
        for (String path : new String[] {"/admin/users/123/grants/new", "/admin/users/abc", "/admin/users/123",
                "/admin/users/123/delete", "/admin/users/" + bob + "/grants/" + billingViewer + "/delete",
                "/admin/applications/123/role-options", "/admin/applications/abc/role-options",
                "/admin/applications"}) {
            as(admin).when().get(path).then().statusCode(404).body(emptyString());
        }
        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/users/123")
                .then().statusCode(404).body(emptyString());
        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/users/" + bob + "/grants/" + billingViewer)
                .then().statusCode(404).body(emptyString());
    }

    @Test
    void readerGets403OnEveryUserWrite() {
        long bob = database.idOf("app_user", "bob");
        long crmAdmin = database.idOf("role", "crm/admin");
        long billing = database.idOf("application", "billing");
        long billingViewer = database.idOf("role", "billing/viewer");

        writer(reader).formParam("username", "dave").formParam("applicationId", billing)
                .formParam("roleId", billingViewer)
                .when().post("/admin/users").then().statusCode(403);
        writer(reader).formParam("username", "robert")
                .when().put("/admin/users/" + bob).then().statusCode(403);
        writer(reader).when().delete("/admin/users/" + bob).then().statusCode(403);
        writer(reader).formParam("applicationId", billing).formParam("roleId", billingViewer)
                .when().post("/admin/users/" + bob + "/grants").then().statusCode(403);
        as(reader).header("X-CSRF-Token", reader.csrf()).queryParam("confirmedUserIds", bob)
                .when().delete("/admin/users/" + bob + "/grants/" + crmAdmin).then().statusCode(403);
        for (String dialog : new String[] {"/admin/users/new", "/admin/users/" + bob + "/grants/new",
                "/admin/users/" + bob + "/delete", "/admin/users/" + bob + "/grants/" + crmAdmin + "/delete"}) {
            as(reader).when().get(dialog).then().statusCode(403);
        }

        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
        assertThatThrownBy(() -> database.idOf("app_user", "dave")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writesWithoutCsrfTokenAre403() {
        long bob = database.idOf("app_user", "bob");

        as(admin).when().delete("/admin/users/" + bob).then().statusCode(403);

        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
    }

    @Test
    void escapesUsernamesInDialogs() {
        Response created = as(admin).header("X-CSRF-Token", admin.csrf()).contentType(FORM)
                .formParam("username", "<b>x</b>")
                .formParam("applicationId", database.idOf("application", "crm"))
                .formParam("roleId", database.idOf("role", "crm/viewer"))
                .when().post("/admin/users");
        assertThat(created.statusCode()).isEqualTo(204);
        long id = database.idOf("app_user", "<b>x</b>");

        String body = as(admin).when().get("/admin/users/" + id + "/grants/" + database.idOf("role", "crm/viewer")
                + "/delete").then().statusCode(200).extract().asString();

        assertThat(body).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }

    private static void assertNoInlineCode(String body) {
        assertThat(body).doesNotContain("<style", " style=", " onclick=", "hx-on");
        assertThat(Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>").matcher(body).find())
                .as("inline <script> without src").isFalse();
    }

    private static RequestSpecification as(AdminCookies cookies) {
        return given().port(server.port()).redirects().follow(false).cookie(SESSION_COOKIE, cookies.session());
    }

    private static RequestSpecification writer(AdminCookies cookies) {
        return as(cookies).header("X-CSRF-Token", cookies.csrf()).contentType(FORM);
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
