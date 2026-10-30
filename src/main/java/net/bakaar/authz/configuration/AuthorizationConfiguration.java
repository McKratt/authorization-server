package net.bakaar.authz.configuration;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRouting;
import java.util.Optional;
import net.bakaar.authz.adapter.in.web.admin.AuthorizationAdminAdapterConfiguration;
import net.bakaar.authz.adapter.in.web.admin.AuthorizationAdminAdapterConfiguration.ApplicationAdminPorts;
import net.bakaar.authz.adapter.in.web.admin.AuthorizationAdminAdapterConfiguration.UserAdminPorts;
import net.bakaar.authz.adapter.in.web.api.AuthorizationApiAdapterConfiguration;
import net.bakaar.authz.adapter.out.idp.AuthorizationIdpAdapterConfiguration;
import net.bakaar.authz.adapter.out.persistence.AuthorizationPersistenceAdapterConfiguration;
import net.bakaar.authz.application.port.out.AccessTokenVerifier;
import net.bakaar.authz.application.service.ApplicationAdminService;
import net.bakaar.authz.application.service.AuthorizationService;
import net.bakaar.authz.application.service.UserAdminService;

/**
 * Wiring of the authorization context: outgoing adapters, then application services, then incoming adapters.
 * No container. Closing it closes the connection pool.
 */
public final class AuthorizationConfiguration implements AutoCloseable {

    private final AuthorizationPersistenceAdapterConfiguration persistence;
    private final AuthorizationService authorization;
    private final Optional<AuthorizationAdminAdapterConfiguration> admin;
    private final UserAdminService userAdmin;
    private final ApplicationAdminService applicationAdmin;

    /**
     * @throws RuntimeException when a setting is invalid; settings are validated before the pool exists, so a bad
     *                          value cannot leak it
     */
    public AuthorizationConfiguration(Config config) {
        AccessTokenVerifier accessTokenVerifier =
                AuthorizationIdpAdapterConfiguration.accessTokenVerifier(config.get("jwt"));
        admin = AuthorizationAdminAdapterConfiguration.fromConfig(config.get("admin"));
        persistence = new AuthorizationPersistenceAdapterConfiguration(config.get("db"));

        authorization = new AuthorizationService(accessTokenVerifier, persistence.userRolesFinder());
        userAdmin = new UserAdminService(persistence.transactionRunner(), persistence.userStore(),
                persistence.applicationStore(), persistence.grantStore());
        applicationAdmin = new ApplicationAdminService(persistence.transactionRunner(),
                persistence.applicationStore(), persistence.grantStore(), persistence.userStore());
    }

    /** The admin UI is routed only when {@code admin.oidc.issuer} is configured. */
    public void routing(HttpRouting.Builder routing) {
        admin.ifPresent(adminUi -> adminUi.routing(routing,
                new UserAdminPorts(userAdmin, userAdmin, userAdmin, userAdmin, userAdmin, userAdmin, userAdmin,
                        userAdmin),
                new ApplicationAdminPorts(applicationAdmin, applicationAdmin, applicationAdmin, applicationAdmin,
                        applicationAdmin, applicationAdmin, applicationAdmin, applicationAdmin, applicationAdmin)));
        AuthorizationApiAdapterConfiguration.routing(routing, authorization);
    }

    @Override
    public void close() {
        persistence.close();
    }
}
