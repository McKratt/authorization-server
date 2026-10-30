package net.bakaar.authz.configuration;

import io.helidon.config.Config;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application-wide assembly: the authorization context plus the application-wide error handlers. Used by
 * {@code main} and by the {@code *IT} tests, so the tests run the real wiring.
 */
public final class AuthorizationServerConfiguration {

    private static final Logger LOGGER = Logger.getLogger(AuthorizationServerConfiguration.class.getName());

    private AuthorizationServerConfiguration() {
    }

    /**
     * Starts the web server. A failed start closes the connection pool.
     */
    public static RunningServer start(Config config) {
        AuthorizationConfiguration authorization = new AuthorizationConfiguration(config);
        try {
            WebServer webServer = WebServer.builder()
                    .config(config.get("server"))
                    .routing(routing -> routing(routing, authorization))
                    .build()
                    .start();
            return new RunningServer(webServer, authorization);
        } catch (RuntimeException e) {
            authorization.close();
            throw e;
        }
    }

    private static void routing(HttpRouting.Builder routing, AuthorizationConfiguration authorization) {
        authorization.routing(routing);
        // Helidon's own HTTP errors, e.g. 404 for an unknown route: their status, no body
        routing.error(HttpException.class, (request, response, exception) ->
                        response.status(exception.status()).send())
                .error(Exception.class, (request, response, exception) -> {
                    // never leak exception details to the caller
                    LOGGER.log(Level.SEVERE, "Unhandled error on %s".formatted(request.path().path()), exception);
                    response.status(Status.INTERNAL_SERVER_ERROR_500).send();
                });
    }

    public record RunningServer(WebServer webServer, AuthorizationConfiguration authorization)
            implements AutoCloseable {

        public int port() {
            return webServer.port();
        }

        @Override
        public void close() {
            webServer.stop();
            authorization.close();
        }
    }
}
