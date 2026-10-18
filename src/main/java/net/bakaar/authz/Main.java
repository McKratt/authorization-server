package net.bakaar.authz;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bakaar.authz.adapter.in.web.admin.UsersRoutes;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.adapter.in.web.admin.auth.LoginRoutes;
import net.bakaar.authz.adapter.in.web.admin.auth.OidcClient;
import net.bakaar.authz.adapter.in.web.admin.auth.OidcSettings;
import net.bakaar.authz.adapter.in.web.admin.auth.SessionCodec;
import net.bakaar.authz.adapter.in.web.admin.auth.SessionCookies;
import net.bakaar.authz.adapter.in.web.api.AuthorizationApi;
import net.bakaar.authz.adapter.out.idp.JwtAccessTokenVerifier;
import net.bakaar.authz.adapter.out.idp.JwtSettings;
import net.bakaar.authz.adapter.out.persistence.Database;
import net.bakaar.authz.adapter.out.persistence.UserRolesPersistenceAdapter;
import net.bakaar.authz.application.domain.service.AuthorizationService;
import net.bakaar.authz.application.port.in.GetAuthorization;

public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        Config config = Config.create();
        Config.global(config);

        RunningServer server = start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        LOGGER.info(() -> "Server started on http://localhost:" + server.port());
    }

    /**
     * Composition root: wires outgoing adapters, domain services and incoming adapters.
     */
    public static RunningServer start(Config config) {
        HikariDataSource dataSource = Database.dataSource(config.get("db"));
        if (config.get("db.migrate").asBoolean().orElse(true)) {
            Database.migrate(dataSource);
        }
        GetAuthorization getAuthorization = new AuthorizationService(
                new JwtAccessTokenVerifier(JwtSettings.fromConfig(config.get("jwt"))),
                new UserRolesPersistenceAdapter(dataSource));
        Consumer<HttpRouting.Builder> adminUi = adminUi(config.get("admin"));

        WebServer webServer = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing(routing, getAuthorization, adminUi))
                .build()
                .start();
        return new RunningServer(webServer, dataSource);
    }

    /**
     * Routes of the admin UI, or none when {@code admin.oidc.issuer} is not configured. Nothing here contacts
     * Keycloak: discovery happens on the first sign-in, so the server starts while Keycloak is unreachable.
     */
    private static Consumer<HttpRouting.Builder> adminUi(Config admin) {
        Optional<OidcSettings> oidcSettings = OidcSettings.fromConfig(admin);
        if (oidcSettings.isEmpty()) {
            LOGGER.warning("admin.oidc.issuer is not configured: the admin UI is disabled");
            return routing -> { };
        }
        OidcSettings settings = oidcSettings.get();
        SessionCookies cookies = new SessionCookies(
                new SessionCodec(sessionKey(admin.get("session-key"))),
                admin.get("cookie-secure").asBoolean().orElse(true));
        OidcClient oidc = new OidcClient(settings, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        LoginRoutes login = new LoginRoutes(oidc, settings, cookies,
                admin.get("session-duration").as(Duration.class).orElse(Duration.ofHours(8)));
        return routing -> routing.addFilter(new AdminAuthFilter(cookies))
                .register(login)
                .register("/admin", new UsersRoutes());
    }

    /**
     * Base64 of 32 random bytes, e.g. {@code openssl rand -base64 32}. The value never appears in messages.
     */
    private static byte[] sessionKey(Config sessionKey) {
        String encoded = sessionKey.asString().orElseThrow(() ->
                new IllegalStateException("admin.session-key must be set when the admin UI is configured"));
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("admin.session-key must be base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException("admin.session-key must decode to 32 bytes");
        }
        return key;
    }

    static void routing(HttpRouting.Builder routing,
                        GetAuthorization getAuthorization,
                        Consumer<HttpRouting.Builder> adminUi) {
        adminUi.accept(routing);
        routing.register("/api/v1/authorizations", new AuthorizationApi(getAuthorization))
                .error(Exception.class, (request, response, exception) -> {
                    // never leak exception details to the caller
                    LOGGER.log(Level.SEVERE, "Unhandled error on " + request.path().path(), exception);
                    response.status(Status.INTERNAL_SERVER_ERROR_500).send();
                });
    }

    public record RunningServer(WebServer webServer, HikariDataSource dataSource) implements AutoCloseable {

        public int port() {
            return webServer.port();
        }

        @Override
        public void close() {
            webServer.stop();
            dataSource.close();
        }
    }
}
