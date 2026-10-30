package net.bakaar.authz.adapter.in.web.admin;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.helidon.config.Config;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.ClasspathHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;
import net.bakaar.authz.application.port.in.ApplicationChangePreview;
import net.bakaar.authz.application.port.in.ApplicationCreation;
import net.bakaar.authz.application.port.in.ApplicationDeletion;
import net.bakaar.authz.application.port.in.ApplicationListing;
import net.bakaar.authz.application.port.in.ApplicationLookup;
import net.bakaar.authz.application.port.in.ApplicationUpdate;
import net.bakaar.authz.application.port.in.RoleAddition;
import net.bakaar.authz.application.port.in.RoleDeletion;
import net.bakaar.authz.application.port.in.RoleGranting;
import net.bakaar.authz.application.port.in.RoleRevocation;
import net.bakaar.authz.application.port.in.RoleUpdate;
import net.bakaar.authz.application.port.in.UserChangePreview;
import net.bakaar.authz.application.port.in.UserCreation;
import net.bakaar.authz.application.port.in.UserDeletion;
import net.bakaar.authz.application.port.in.UserListing;
import net.bakaar.authz.application.port.in.UserLookup;
import net.bakaar.authz.application.port.in.UserRenaming;
import net.bakaar.authz.domain.NotFoundException;

/**
 * Settings and routes of the admin UI. Nothing here contacts Keycloak: discovery happens on the first sign-in, so the
 * server starts while Keycloak is unreachable.
 */
public final class AuthorizationAdminAdapterConfiguration {

    private static final Logger LOGGER = Logger.getLogger(AuthorizationAdminAdapterConfiguration.class.getName());

    private final OidcSettings oidc;
    private final byte[] sessionKey;
    private final boolean cookieSecure;
    private final Duration sessionDuration;

    private AuthorizationAdminAdapterConfiguration(OidcSettings oidc, byte[] sessionKey, boolean cookieSecure,
                                                   Duration sessionDuration) {
        this.oidc = oidc;
        this.sessionKey = sessionKey;
        this.cookieSecure = cookieSecure;
        this.sessionDuration = sessionDuration;
    }

    /**
     * Settings of the admin UI, or empty (with a WARNING) when {@code admin.oidc.issuer} is not configured.
     *
     * @param admin the {@code admin} config node
     * @throws RuntimeException when the admin UI is configured with an invalid value
     */
    public static Optional<AuthorizationAdminAdapterConfiguration> fromConfig(Config admin) {
        Optional<OidcSettings> oidcSettings = OidcSettings.fromConfig(admin);
        if (oidcSettings.isEmpty()) {
            LOGGER.warning("admin.oidc.issuer is not configured: the admin UI is disabled");
            return Optional.empty();
        }
        return Optional.of(new AuthorizationAdminAdapterConfiguration(
                oidcSettings.get(),
                sessionKey(admin.get("session-key")),
                admin.get("cookie-secure").asBoolean().orElse(true),
                admin.get("session-duration").as(Duration.class).orElse(Duration.ofHours(8))));
    }

    public void routing(HttpRouting.Builder routing, UserAdminPorts users, ApplicationAdminPorts applications) {
        SessionCookies cookies = new SessionCookies(new SessionCodec(sessionKey), cookieSecure);
        OidcClient oidcClient = new OidcClient(oidc, WebClient.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(OidcClient.TIMEOUT)
                .followRedirects(false)
                .build());
        // precompiled by jte-maven-plugin: rendering needs no Java compiler at runtime
        AdminPages pages = new AdminPages(TemplateEngine.createPrecompiled(ContentType.Html));
        routing.addFilter(new AdminAuthFilter(cookies, pages))
                .register("/admin/static", StaticContentFeature.createService(ClasspathHandlerConfig.builder()
                        .location("admin-static")
                        .build()))
                .register(new LoginController(oidcClient, oidc, cookies, sessionDuration, pages))
                .register("/admin/applications", new ApplicationsController(pages, applications))
                .register("/admin", new UsersController(pages, users, applications))
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

    /** The user use cases the admin UI calls. */
    public record UserAdminPorts(UserListing list, UserLookup get, UserCreation create, UserRenaming rename,
                                 UserDeletion delete, RoleGranting grantRoles, RoleRevocation revokeRole,
                                 UserChangePreview preview) {
    }

    /** The application use cases the admin UI calls. */
    public record ApplicationAdminPorts(ApplicationListing list, ApplicationLookup get, ApplicationCreation create,
                                        ApplicationUpdate update, ApplicationDeletion delete, RoleAddition addRole,
                                        RoleUpdate updateRole, RoleDeletion deleteRole,
                                        ApplicationChangePreview preview) {
    }
}
