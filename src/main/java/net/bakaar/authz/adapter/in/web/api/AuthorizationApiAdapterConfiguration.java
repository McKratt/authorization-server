package net.bakaar.authz.adapter.in.web.api;

import io.helidon.webserver.http.HttpRules;
import net.bakaar.authz.application.port.in.AuthorizationLookup;

/**
 * Registers the JSON authorization API for client applications.
 */
public final class AuthorizationApiAdapterConfiguration {

    private AuthorizationApiAdapterConfiguration() {
    }

    public static void routing(HttpRules rules, AuthorizationLookup authorizationLookup) {
        rules.register("/api/v1/authorizations", new AuthorizationController(authorizationLookup));
    }
}
