package net.bakaar.authz;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import java.util.logging.Level;
import java.util.logging.Logger;
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

        WebServer webServer = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing(routing, getAuthorization))
                .build()
                .start();
        return new RunningServer(webServer, dataSource);
    }

    static void routing(HttpRouting.Builder routing, GetAuthorization getAuthorization) {
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
