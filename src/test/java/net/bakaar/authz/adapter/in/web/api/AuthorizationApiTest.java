package net.bakaar.authz.adapter.in.web.api;

import static io.restassured.RestAssured.given;
import static net.bakaar.authz.support.TestIdentityProvider.AUDIENCE;
import static net.bakaar.authz.support.TestIdentityProvider.ISSUER;
import static net.bakaar.authz.support.TestIdentityProvider.validClaims;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import net.bakaar.authz.Main;
import net.bakaar.authz.support.TestDatabase;
import net.bakaar.authz.support.TestIdentityProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End to end: real server, real PostgreSQL, WireMock as identity provider.
 */
class AuthorizationApiTest {

    private static TestDatabase database;
    private static TestIdentityProvider idp;
    private static Main.RunningServer server;

    @BeforeAll
    static void setUp() {
        database = new TestDatabase();
        idp = new TestIdentityProvider();
        server = Main.start(Config.just(ConfigSources.create(Map.of(
                "server.port", "0",
                "db.url", database.jdbcUrl(),
                "db.username", database.username(),
                "db.password", database.password(),
                "jwt.issuer", ISSUER,
                "jwt.jwks-uri", idp.jwksUri(),
                "jwt.audiences", AUDIENCE,
                "jwt.allow-insecure-jwks-uri", "true"))));
        database.insertFixtures();
    }

    @AfterAll
    static void tearDown() {
        server.close();
        idp.close();
        database.close();
    }

    @Test
    void returnsRolesOfTheTokenOwnerForTheApplication() {
        api(idp.tokenFor("alice"))
                .queryParam("application", "billing")
                .when().get()
                .then().statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", "no-store")
                .body("username", equalTo("alice"))
                .body("application", equalTo("billing"))
                .body("roles", contains("admin", "viewer"));
    }

    @Test
    void returnsOnlyTheRequestedApplicationRoles() {
        api(idp.tokenFor("alice"))
                .queryParam("application", "crm")
                .when().get()
                .then().statusCode(200)
                .body("roles", contains("viewer"));
    }

    @Test
    void ignoresAnyAttemptToNameAnotherUser() {
        api(idp.tokenFor("alice"))
                .queryParam("application", "crm")
                .queryParam("username", "bob")
                .queryParam("user", "bob")
                .header("X-Forwarded-User", "bob")
                .when().get()
                .then().statusCode(200)
                .body("username", equalTo("alice"))
                .body("roles", contains("viewer"));
    }

    @Test
    void returns404WhenUserHasNoRoleForTheApplication() {
        api(idp.tokenFor("bob"))
                .queryParam("application", "billing")
                .when().get()
                .then().statusCode(404)
                .body("error", equalTo("not_found"))
                .body("$", not(hasKey("roles")));
    }

    @Test
    void returnsTheSame404ForUnknownApplicationAndUnknownUser() {
        api(idp.tokenFor("alice")).queryParam("application", "unknown")
                .when().get().then().statusCode(404).body("error", equalTo("not_found"));
        api(idp.tokenFor("mallory")).queryParam("application", "billing")
                .when().get().then().statusCode(404).body("error", equalTo("not_found"));
    }

    @Test
    void returns401WithoutToken() {
        given().port(server.port()).basePath("/api/v1/authorizations")
                .queryParam("application", "billing")
                .when().get()
                .then().statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("$", not(hasKey("roles")));
    }

    @Test
    void returns401ForNonBearerScheme() {
        given().port(server.port()).basePath("/api/v1/authorizations")
                .header("Authorization", "Basic YWxpY2U6c2VjcmV0")
                .queryParam("application", "billing")
                .when().get()
                .then().statusCode(401);
    }

    @Test
    void returns401ForTwoAuthorizationHeaders() {
        given().port(server.port()).basePath("/api/v1/authorizations")
                .header("Authorization", "Bearer " + idp.tokenFor("bob"))
                .header("Authorization", "Bearer " + idp.tokenFor("alice"))
                .queryParam("application", "billing")
                .when().get()
                .then().statusCode(401);
    }

    @Test
    void returns401ForUntrustedToken() {
        RequestSpecification forged = api(TestIdentityProvider.sign(validClaims("alice").build(),
                TestIdentityProvider.generateKey(TestIdentityProvider.KEY_ID)));
        forged.queryParam("application", "billing")
                .when().get()
                .then().statusCode(401)
                .header("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                .body("error", equalTo("invalid_token"));
    }

    @Test
    void returns401ForExpiredToken() {
        String expired = idp.sign(validClaims("alice")
                .expirationTime(Date.from(Instant.now().minusSeconds(120))).build());
        api(expired).queryParam("application", "billing")
                .when().get().then().statusCode(401);
    }

    @Test
    void returns401BeforeValidatingApplicationParameter() {
        given().port(server.port()).basePath("/api/v1/authorizations")
                .when().get()
                .then().statusCode(401);
    }

    @Test
    void returns400WithoutApplication() {
        api(idp.tokenFor("alice")).when().get().then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    @Test
    void returns400ForRepeatedApplication() {
        api(idp.tokenFor("alice"))
                .queryParam("application", "crm", "billing")
                .when().get()
                .then().statusCode(400);
    }

    @Test
    void returns400ForInvalidApplicationName() {
        api(idp.tokenFor("alice")).queryParam("application", "billing' OR '1'='1")
                .when().get().then().statusCode(400);
        api(idp.tokenFor("alice")).queryParam("application", "%")
                .when().get().then().statusCode(400);
    }

    @Test
    void healthEndpointIsUp() {
        given().port(server.port()).when().get("/observe/health").then().statusCode(204);
    }

    private static RequestSpecification api(String token) {
        return given().port(server.port())
                .basePath("/api/v1/authorizations")
                .header("Authorization", "Bearer " + token);
    }
}
