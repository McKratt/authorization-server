package net.bakaar.authz;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import java.util.logging.Logger;
import net.bakaar.authz.configuration.AuthorizationServerConfiguration;
import net.bakaar.authz.configuration.AuthorizationServerConfiguration.RunningServer;

public final class AuthorizationServerApplication {

    private static final Logger LOGGER = Logger.getLogger(AuthorizationServerApplication.class.getName());

    private AuthorizationServerApplication() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        // system properties, then environment variables (DB_URL...), then application.yaml
        Config config = Config.create();
        RunningServer server = AuthorizationServerConfiguration.start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        LOGGER.info(() -> "Server started on http://localhost:" + server.port());
    }
}
