package net.bakaar.authz;

import com.zaxxer.hikari.HikariDataSource;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.helidon.config.Config;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.ClasspathHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.bakaar.authz.adapter.in.web.admin.AdminPages;
import net.bakaar.authz.adapter.in.web.admin.ApplicationAdminPorts;
import net.bakaar.authz.adapter.in.web.admin.ApplicationsRoutes;
import net.bakaar.authz.adapter.in.web.admin.UserAdminPorts;
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
import net.bakaar.authz.adapter.out.persistence.JdbcApplicationStore;
import net.bakaar.authz.adapter.out.persistence.JdbcGrantStore;
import net.bakaar.authz.adapter.out.persistence.JdbcTransactions;
import net.bakaar.authz.adapter.out.persistence.JdbcUserStore;
import net.bakaar.authz.adapter.out.persistence.UserRolesPersistenceAdapter;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.service.ApplicationAdminService;
import net.bakaar.authz.application.domain.service.AuthorizationService;
import net.bakaar.authz.application.domain.service.UserAdminService;
import net.bakaar.authz.application.port.in.GetAuthorization;

public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        Config config = Config.create();
        Services.set(Config.class, config);

        RunningServer server = start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        LOGGER.info(() -> "Server started on http://localhost:" + server.port());
    }

    /**
     * Composition root: wires outgoing adapters, domain services and incoming adapters.
     */
    public static RunningServer start(Config config) {
        // configuration is validated before the pool exists, so a bad value cannot leak it
        JwtSettings jwtSettings = JwtSettings.fromConfig(config.get("jwt"));
        Optional<AdminConfig> adminConfig = adminConfig(config.get("admin"));
        HikariDataSource dataSource = Database.dataSource(config.get("db"));
        try {
            if (config.get("db.migrate").asBoolean().orElse(true)) {
                Database.migrate(dataSource);
            }
            GetAuthorization getAuthorization = new AuthorizationService(
                    new JwtAccessTokenVerifier(jwtSettings),
                    new UserRolesPersistenceAdapter(dataSource));
            Consumer<HttpRouting.Builder> adminUi = adminConfig
                    .map(admin -> adminUi(admin, dataSource))
                    .orElse(routing -> {
                    });

            WebServer webServer = WebServer.builder()
                    .config(config.get("server"))
                    .routing(routing -> routing(routing, getAuthorization, adminUi))
                    .build()
                    .start();
            return new RunningServer(webServer, dataSource);
        } catch (RuntimeException e) {
            dataSource.close();
            throw e;
        }
    }

    /**
     * Settings of the admin UI, or empty (with a WARNING) when {@code admin.oidc.issuer} is not configured.
     *
     * @throws RuntimeException when the admin UI is configured with an invalid value
     */
    private static Optional<AdminConfig> adminConfig(Config admin) {
        Optional<OidcSettings> oidcSettings = OidcSettings.fromConfig(admin);
        if (oidcSettings.isEmpty()) {
            LOGGER.warning("admin.oidc.issuer is not configured: the admin UI is disabled");
            return Optional.empty();
        }
        return Optional.of(new AdminConfig(
                oidcSettings.get(),
                sessionKey(admin.get("session-key")),
                admin.get("cookie-secure").asBoolean().orElse(true),
                admin.get("session-duration").as(Duration.class).orElse(Duration.ofHours(8))));
    }

    private record AdminConfig(OidcSettings oidc, byte[] sessionKey, boolean cookieSecure, Duration sessionDuration) {
    }

    /**
     * Routes of the admin UI. Nothing here contacts Keycloak: discovery happens on the first sign-in, so the server
     * starts while Keycloak is unreachable.
     */
    private static Consumer<HttpRouting.Builder> adminUi(AdminConfig admin, HikariDataSource dataSource) {
        OidcSettings settings = admin.oidc();
        SessionCookies cookies = new SessionCookies(new SessionCodec(admin.sessionKey()), admin.cookieSecure());
        OidcClient oidc = new OidcClient(settings, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        // precompiled by jte-maven-plugin: rendering needs no Java compiler at runtime
        AdminPages pages = new AdminPages(TemplateEngine.createPrecompiled(ContentType.Html));
        LoginRoutes login = new LoginRoutes(oidc, settings, cookies, admin.sessionDuration(), pages);
        JdbcTransactions transactions = new JdbcTransactions(dataSource);
        JdbcUserStore userStore = new JdbcUserStore(transactions);
        JdbcApplicationStore applicationStore = new JdbcApplicationStore(transactions);
        JdbcGrantStore grantStore = new JdbcGrantStore(transactions);
        UserAdminService userAdmin = new UserAdminService(transactions, userStore, applicationStore, grantStore);
        ApplicationAdminService applicationAdmin = new ApplicationAdminService(transactions, applicationStore,
                grantStore, userStore);
        UserAdminPorts userPorts = new UserAdminPorts(userAdmin, userAdmin, userAdmin, userAdmin, userAdmin,
                userAdmin, userAdmin, userAdmin);
        ApplicationAdminPorts applicationPorts = new ApplicationAdminPorts(applicationAdmin, applicationAdmin,
                applicationAdmin, applicationAdmin, applicationAdmin, applicationAdmin, applicationAdmin,
                applicationAdmin, applicationAdmin);
        return routing -> routing.addFilter(new AdminAuthFilter(cookies, pages))
                .register("/admin/static", StaticContentFeature.createService(ClasspathHandlerConfig.builder()
                        .location("admin-static")
                        .build()))
                .register(login)
                .register("/admin/applications", new ApplicationsRoutes(pages, applicationPorts))
                .register("/admin", new UsersRoutes(pages, userPorts, applicationPorts))
                // unknown or malformed IDs in admin paths and forms: 404, no body
                .error(NotFoundException.class, AdminPages::notFound);
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
        } catch (IllegalArgumentException _) {
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
                // Helidon's own HTTP errors, e.g. 404 for an unknown route: their status, no body
                .error(HttpException.class, (request, response, exception) ->
                        response.status(exception.status()).send())
                .error(Exception.class, (request, response, exception) -> {
                    // never leak exception details to the caller
                    LOGGER.log(Level.SEVERE, "Unhandled error on %s".formatted(request.path().path()), exception);
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
