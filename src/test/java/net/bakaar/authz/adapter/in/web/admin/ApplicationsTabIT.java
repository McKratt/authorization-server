package net.bakaar.authz.adapter.in.web.admin;

import static io.restassured.RestAssured.given;
import static net.bakaar.authz.support.AdminClient.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.emptyString;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.restassured.specification.RequestSpecification;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
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
 * Applications tab, application dialog and its writes, end to end: real server, real PostgreSQL,
 * {@link FakeKeycloak} as identity provider. The fixtures are reset before every test.
 */
class ApplicationsTabTest {

    private static final String CHANGED_TRIGGER = "{\"authz:changed\":true,\"authz:close-dialog\":true}";
    private static final String DIALOG_CHANGED_TRIGGER = "{\"authz:changed\":true}";
    private static final String FORM = "application/x-www-form-urlencoded; charset=UTF-8";
    private static final String RENAME_WARNING =
            "Renaming changes what client applications must send to the authorization API.";
    private static final String ROLE_RENAME_WARNING =
            "Renaming a role changes the role names client applications receive from the authorization API.";

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

    // ------------------------------------------------------------------ tab

    @Test
    void tabListsApplicationsWithResponsibleRolesAndUserCount() {
        String body = as(admin).when().get("/admin/applications").then().statusCode(200)
                .header("Cache-Control", "no-store")
                .extract().asString();

        String crmRow = row(body, "crm");
        assertThat(crmRow).contains("data-filter=\"crm crm@bakaar.net admin viewer\"", "crm@bakaar.net",
                ">admin<", ">viewer<", ">2<",
                "hx-get=\"/admin/applications/" + database.idOf("application", "crm") + "\"");
        assertThat(body).contains("<html", "billing@bakaar.net", "+ New application", "id=\"filter\"",
                "aria-current=\"page\">Applications<");
        assertNoInlineCode(body);
    }

    @Test
    void htmxTabRequestGetsFragment() {
        String body = as(admin).header("HX-Request", "true").when().get("/admin/applications")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("id=\"tab-content\"", "hx-get=\"/admin/applications\"", "hx-swap-oob",
                        "crm@bakaar.net")
                .doesNotContain("<html", "<head");
    }

    @Test
    void tabListsApplicationsNobodyUses() {
        writer(admin).formParam("name", "wiki").formParam("responsible", "docs@bakaar.net")
                .when().post("/admin/applications").then().statusCode(204);

        String body = as(admin).when().get("/admin/applications").then().statusCode(200).extract().asString();

        assertThat(row(body, "wiki")).contains("docs@bakaar.net", ">0<");
    }

    @Test
    void readerSeesTheTabWithoutTheNewButton() {
        String body = as(reader).when().get("/admin/applications").then().statusCode(200).extract().asString();

        assertThat(body).contains("crm@bakaar.net").doesNotContain("+ New application", "/admin/applications/new");
    }

    // ------------------------------------------------------------------ dialogs

    @Test
    void newApplicationDialogPostsNameResponsibleDescriptionAndRoles() {
        String body = as(admin).when().get("/admin/applications/new").then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "New application", "hx-post=\"/admin/applications\"",
                        "name=\"name\"", "name=\"responsible\"", "name=\"description\"", "name=\"roleName\"",
                        "name=\"roleDescription\"", "data-add-role", "<template")
                .doesNotContain(RENAME_WARNING);
        assertNoInlineCode(body);
    }

    @Test
    void applicationDialogShowsTheApplicationWithItsRolesAndRenameWarning() {
        long crm = database.idOf("application", "crm");
        long crmAdmin = database.idOf("role", "crm/admin");

        String body = as(admin).when().get("/admin/applications/" + crm).then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "value=\"crm\"", "value=\"crm@bakaar.net\"", RENAME_WARNING,
                ROLE_RENAME_WARNING,
                "hx-put=\"/admin/applications/" + crm + "\"",
                "hx-get=\"/admin/applications/" + crm + "/delete\"",
                "hx-post=\"/admin/applications/" + crm + "/roles\"",
                "hx-put=\"/admin/applications/" + crm + "/roles/" + crmAdmin + "\"",
                "hx-get=\"/admin/applications/" + crm + "/roles/" + crmAdmin + "/delete\"",
                "value=\"admin\"", "value=\"viewer\"");
        assertNoInlineCode(body);
    }

    @Test
    void readerSeesReadOnlyDialog() {
        long crm = database.idOf("application", "crm");

        String body = as(reader).when().get("/admin/applications/" + crm).then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "crm", "crm@bakaar.net", "admin", "viewer")
                .doesNotContain("hx-put", "hx-post", "hx-delete", "/delete\"", RENAME_WARNING);
        assertThat(Pattern.compile("<input(?![^>]*\\sdisabled)[^>]*>").matcher(body).find())
                .as("an <input> without disabled").isFalse();
        assertThat(body).doesNotContain("<textarea");
    }

    // ------------------------------------------------------------------ create

    @Test
    void createApplicationWithRolesThenItAppears() {
        writer(admin)
                .formParam("name", "wiki")
                .formParam("responsible", " docs@bakaar.net ")
                .formParam("description", "Internal documentation")
                .formParam("roleName", "reader", "", "editor")
                .formParam("roleDescription", "Read pages", "", "Edit pages")
                .when().post("/admin/applications")
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER)
                .body(emptyString());

        String tab = as(admin).when().get("/admin/applications").then().statusCode(200).extract().asString();
        assertThat(row(tab, "wiki")).contains("docs@bakaar.net", ">reader<", ">editor<", ">0<");
        long wiki = database.idOf("application", "wiki");
        String dialog = as(admin).when().get("/admin/applications/" + wiki).then().statusCode(200)
                .extract().asString();
        assertThat(dialog).contains("Internal documentation", "value=\"Read pages\"", "value=\"Edit pages\"");
    }

    @Test
    void createApplicationWithInvalidEmailIs422() {
        String body = writer(admin)
                .formParam("name", "-wiki")
                .formParam("responsible", "docs-at-bakaar")
                .formParam("description", "Internal documentation")
                .formParam("roleName", "reader")
                .formParam("roleDescription", "Read pages")
                .when().post("/admin/applications")
                .then().statusCode(422)
                .extract().asString();

        // every field error at once, inputs kept
        assertThat(body).contains("<dialog", "hx-post=\"/admin/applications\"",
                "Enter an email address like name@example.com.",
                "Use 1 to 100 letters, digits",
                "value=\"-wiki\"", "value=\"docs-at-bakaar\"", "Internal documentation",
                "value=\"reader\"", "value=\"Read pages\"",
                "aria-describedby=\"responsible-error\"", "aria-describedby=\"name-error\"");
        assertThatThrownBy(() -> database.idOf("application", "-wiki")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createApplicationWithInvalidOrDuplicateRoleNamesIs422() {
        String invalid = writer(admin)
                .formParam("name", "wiki").formParam("responsible", "docs@bakaar.net")
                .formParam("roleName", "reader", "has space")
                .formParam("roleDescription", "", "")
                .when().post("/admin/applications")
                .then().statusCode(422).extract().asString();
        String duplicate = writer(admin)
                .formParam("name", "wiki").formParam("responsible", "docs@bakaar.net")
                .formParam("roleName", "reader", "reader")
                .formParam("roleDescription", "", "")
                .when().post("/admin/applications")
                .then().statusCode(422).extract().asString();

        assertThat(invalid).contains("Use 1 to 100 letters, digits", "value=\"has space\"",
                "aria-describedby=\"roleName-error\"");
        assertThat(duplicate).contains("Role names must be unique");
        assertThatThrownBy(() -> database.idOf("application", "wiki")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateApplicationNameIs409() {
        String body = writer(admin)
                .formParam("name", "crm")
                .formParam("responsible", "other@bakaar.net")
                .when().post("/admin/applications")
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "This application name is already taken.", "value=\"crm\"",
                "value=\"other@bakaar.net\"");
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateApplicationReturns204AndRenames() {
        long crm = database.idOf("application", "crm");

        writer(admin)
                .formParam("name", "sales")
                .formParam("responsible", "sales@bakaar.net")
                .formParam("description", "Customers")
                .when().put("/admin/applications/" + crm)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);

        assertThat(database.idOf("application", "sales")).isEqualTo(crm);
        assertThat(as(admin).when().get("/admin/applications/" + crm).then().extract().asString())
                .contains("value=\"sales@bakaar.net\"", "Customers");
    }

    @Test
    void updateApplicationRerendersTheDialogOnErrors() {
        long crm = database.idOf("application", "crm");

        String conflict = writer(admin)
                .formParam("name", "billing").formParam("responsible", "crm@bakaar.net")
                .when().put("/admin/applications/" + crm)
                .then().statusCode(409).extract().asString();
        String invalid = writer(admin)
                .formParam("name", "crm").formParam("responsible", "nope")
                .formParam("description", "x".repeat(2001))
                .when().put("/admin/applications/" + crm)
                .then().statusCode(422).extract().asString();

        assertThat(conflict).contains("<dialog", "This application name is already taken.", "value=\"billing\"",
                "hx-put=\"/admin/applications/" + crm + "\"");
        assertThat(invalid).contains("Enter an email address", "Use at most 2000 characters.", "value=\"nope\"");
        assertThat(database.idOf("application", "crm")).isEqualTo(crm);
    }

    // ------------------------------------------------------------------ roles

    @Test
    void addRoleReturnsRefreshedDialog() {
        long crm = database.idOf("application", "crm");

        String body = writer(admin)
                .formParam("name", "agent")
                .formParam("description", "Handles tickets")
                .when().post("/admin/applications/" + crm + "/roles")
                .then().statusCode(200)
                .header("HX-Trigger", DIALOG_CHANGED_TRIGGER)
                .header("Cache-Control", "no-store")
                .extract().asString();

        assertThat(body).contains("<dialog", "value=\"agent\"", "value=\"Handles tickets\"",
                "hx-put=\"/admin/applications/" + crm + "/roles/" + database.idOf("role", "crm/agent") + "\"");
        assertNoInlineCode(body);
    }

    @Test
    void duplicateRoleNameIs409() {
        long crm = database.idOf("application", "crm");

        String body = writer(admin)
                .formParam("name", "admin")
                .when().post("/admin/applications/" + crm + "/roles")
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "This role name is already taken in this application.",
                "aria-describedby=\"roleName-error\"");
    }

    @Test
    void invalidRoleNameIs422() {
        long crm = database.idOf("application", "crm");

        String body = writer(admin)
                .formParam("name", "has space")
                .when().post("/admin/applications/" + crm + "/roles")
                .then().statusCode(422)
                .extract().asString();

        assertThat(body).contains("<dialog", "Use 1 to 100 letters, digits", "value=\"has space\"");
    }

    @Test
    void updateRoleReturnsRefreshedDialog() {
        long crm = database.idOf("application", "crm");
        long crmViewer = database.idOf("role", "crm/viewer");

        String body = writer(admin)
                .formParam("name", "reader")
                .formParam("description", "Reads customers")
                .when().put("/admin/applications/" + crm + "/roles/" + crmViewer)
                .then().statusCode(200)
                .header("HX-Trigger", DIALOG_CHANGED_TRIGGER)
                .extract().asString();

        assertThat(body).contains("value=\"reader\"", "value=\"Reads customers\"");
        assertThat(database.idOf("role", "crm/reader")).isEqualTo(crmViewer);

        String conflict = writer(admin)
                .formParam("name", "admin")
                .when().put("/admin/applications/" + crm + "/roles/" + crmViewer)
                .then().statusCode(409).extract().asString();
        assertThat(conflict).contains("This role name is already taken in this application.");
    }

    @Test
    void deleteRoleConfirmationListsOrphans() {
        long crm = database.idOf("application", "crm");
        long crmAdmin = database.idOf("role", "crm/admin");
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).when().get("/admin/applications/" + crm + "/roles/" + crmAdmin + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "Delete role?", "bob", "confirmedUserIds", String.valueOf(bob),
                        "hx-delete=\"/admin/applications/" + crm + "/roles/" + crmAdmin + "\"")
                .doesNotContain("alice");
        assertNoInlineCode(body);
    }

    @Test
    void deleteRoleWithConfirmationReturnsRefreshedDialog() {
        long crm = database.idOf("application", "crm");
        long crmAdmin = database.idOf("role", "crm/admin");
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).header("X-CSRF-Token", admin.csrf())
                .queryParam("confirmedUserIds", bob)
                .when().delete("/admin/applications/" + crm + "/roles/" + crmAdmin)
                .then().statusCode(200)
                .header("HX-Trigger", DIALOG_CHANGED_TRIGGER)
                .extract().asString();

        assertThat(body).contains("<dialog", "value=\"viewer\"").doesNotContain("value=\"admin\"");
        assertThatThrownBy(() -> database.idOf("app_user", "bob")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteRoleWithStaleConfirmationIs409() {
        long crm = database.idOf("application", "crm");
        long crmAdmin = database.idOf("role", "crm/admin");
        long alice = database.idOf("app_user", "alice");
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).header("X-CSRF-Token", admin.csrf())
                .queryParam("confirmedUserIds", alice)
                .when().delete("/admin/applications/" + crm + "/roles/" + crmAdmin)
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "Delete role?", "bob", "confirmedUserIds", String.valueOf(bob));
        assertThat(database.idOf("role", "crm/admin")).isEqualTo(crmAdmin);
        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
    }

    // ------------------------------------------------------------------ delete application

    @Test
    void deleteApplicationConfirmationListsOrphans() {
        long crm = database.idOf("application", "crm");
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).when().get("/admin/applications/" + crm + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "Delete application?", "bob", "confirmedUserIds",
                        String.valueOf(bob), "hx-delete=\"/admin/applications/" + crm + "\"")
                .doesNotContain("alice", String.valueOf(database.idOf("app_user", "alice")));
        assertNoInlineCode(body);
    }

    @Test
    void deleteApplicationWithConfirmationDeletesOrphans() {
        long crm = database.idOf("application", "crm");
        long alice = database.idOf("app_user", "alice");
        long bob = database.idOf("app_user", "bob");

        as(admin).header("X-CSRF-Token", admin.csrf())
                .queryParam("confirmedUserIds", bob)
                .when().delete("/admin/applications/" + crm)
                .then().statusCode(204)
                .header("HX-Trigger", CHANGED_TRIGGER);

        assertThatThrownBy(() -> database.idOf("application", "crm")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> database.idOf("app_user", "bob")).isInstanceOf(IllegalStateException.class);
        assertThat(database.idOf("app_user", "alice")).isEqualTo(alice);
    }

    @Test
    void deleteApplicationWithoutConfirmationIs409() {
        long crm = database.idOf("application", "crm");
        long bob = database.idOf("app_user", "bob");

        String body = as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/applications/" + crm)
                .then().statusCode(409)
                .extract().asString();

        assertThat(body).contains("<dialog", "Delete application?", "bob", String.valueOf(bob));
        assertThat(database.idOf("application", "crm")).isEqualTo(crm);
    }

    @Test
    void deleteApplicationNobodyLosesIsAPlainConfirmation() {
        long billing = database.idOf("application", "billing");

        String body = as(admin).when().get("/admin/applications/" + billing + "/delete")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("<dialog", "Delete application?", "Delete billing")
                .doesNotContain("confirmedUserIds", "class=\"warn\"");
        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/applications/" + billing)
                .then().statusCode(204);
        assertThatThrownBy(() -> database.idOf("application", "billing"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ errors and access

    @Test
    void unknownIdsAre404() {
        long crm = database.idOf("application", "crm");
        long billingViewer = database.idOf("role", "billing/viewer");
        for (String path : new String[] {"/admin/applications/123", "/admin/applications/abc",
                "/admin/applications/123/delete", "/admin/applications/" + crm + "/roles/" + billingViewer
                + "/delete", "/admin/applications/" + crm + "/roles/abc/delete"}) {
            as(admin).when().get(path).then().statusCode(404).body(emptyString());
        }
        writer(admin).formParam("name", "x").formParam("responsible", "x@bakaar.net")
                .when().put("/admin/applications/123").then().statusCode(404).body(emptyString());
        writer(admin).formParam("name", "x")
                .when().post("/admin/applications/123/roles").then().statusCode(404).body(emptyString());
        as(admin).header("X-CSRF-Token", admin.csrf())
                .when().delete("/admin/applications/" + crm + "/roles/" + billingViewer)
                .then().statusCode(404).body(emptyString());
    }

    @Test
    void readerGets403OnEveryApplicationWrite() {
        long crm = database.idOf("application", "crm");
        long crmAdmin = database.idOf("role", "crm/admin");
        long bob = database.idOf("app_user", "bob");

        writer(reader).formParam("name", "wiki").formParam("responsible", "docs@bakaar.net")
                .when().post("/admin/applications").then().statusCode(403);
        writer(reader).formParam("name", "sales").formParam("responsible", "crm@bakaar.net")
                .when().put("/admin/applications/" + crm).then().statusCode(403);
        as(reader).header("X-CSRF-Token", reader.csrf()).queryParam("confirmedUserIds", bob)
                .when().delete("/admin/applications/" + crm).then().statusCode(403);
        writer(reader).formParam("name", "agent")
                .when().post("/admin/applications/" + crm + "/roles").then().statusCode(403);
        writer(reader).formParam("name", "boss")
                .when().put("/admin/applications/" + crm + "/roles/" + crmAdmin).then().statusCode(403);
        as(reader).header("X-CSRF-Token", reader.csrf()).queryParam("confirmedUserIds", bob)
                .when().delete("/admin/applications/" + crm + "/roles/" + crmAdmin).then().statusCode(403);
        for (String dialog : new String[] {"/admin/applications/new", "/admin/applications/" + crm + "/delete",
                "/admin/applications/" + crm + "/roles/" + crmAdmin + "/delete"}) {
            as(reader).when().get(dialog).then().statusCode(403);
        }

        assertThat(database.idOf("application", "crm")).isEqualTo(crm);
        assertThat(database.idOf("role", "crm/admin")).isEqualTo(crmAdmin);
        assertThat(database.idOf("app_user", "bob")).isEqualTo(bob);
        assertThatThrownBy(() -> database.idOf("application", "wiki")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void escapesStoredValuesInTableAndDialogs() {
        long crm = database.idOf("application", "crm");
        database.execute("UPDATE application SET description = ?, responsible = ? WHERE name = 'crm'",
                "<script>x</script>", "<script>y</script>@bakaar.net");
        database.execute("UPDATE role SET description = ? WHERE name = 'admin'", "<b>z</b>");

        String tab = as(admin).when().get("/admin/applications").then().statusCode(200).extract().asString();
        String dialog = as(admin).when().get("/admin/applications/" + crm).then().statusCode(200)
                .extract().asString();
        String readOnly = as(reader).when().get("/admin/applications/" + crm).then().statusCode(200)
                .extract().asString();
        String rerendered = writer(admin).formParam("name", "<i>w</i>")
                .when().post("/admin/applications/" + crm + "/roles")
                .then().statusCode(422).extract().asString();

        // jte escapes text fully and attribute values minimally ("<" and quotes), both safe
        assertThat(tab).contains("&lt;script&gt;y&lt;/script&gt;@bakaar.net").doesNotContain("<script>y");
        assertThat(dialog).contains("&lt;script&gt;x&lt;/script&gt;</textarea>", "value=\"&lt;script>y",
                "value=\"&lt;b>z");
        assertThat(readOnly).contains("&lt;script&gt;x&lt;/script&gt;", "&lt;script&gt;y&lt;/script&gt;",
                "&lt;b&gt;z&lt;/b&gt;");
        assertThat(rerendered).contains("&lt;script&gt;x&lt;/script&gt;</textarea>", "value=\"&lt;i>w");
        for (String body : new String[] {dialog, readOnly, rerendered}) {
            assertThat(body).doesNotContain("<script", "<b>", "<i>");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The table row of one application, from its data-filter attribute to the end of the row. */
    private static String row(String body, String name) {
        Matcher matcher = Pattern.compile("<tr data-filter=\"" + Pattern.quote(name) + " .*?</tr>", Pattern.DOTALL)
                .matcher(body);
        assertThat(matcher.find()).as("row of " + name).isTrue();
        return matcher.group();
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
