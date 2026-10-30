package net.bakaar.authz.adapter.out.idp;

import io.helidon.config.Config;
import net.bakaar.authz.application.port.out.AccessTokenVerifier;

/**
 * Creates the access token verifier of the identity provider adapter. Keys are fetched lazily, on the first token.
 */
public final class AuthorizationIdpAdapterConfiguration {

    private AuthorizationIdpAdapterConfiguration() {
    }

    /**
     * @param jwt the {@code jwt} config node
     * @throws RuntimeException when a mandatory setting is missing or invalid
     */
    public static AccessTokenVerifier accessTokenVerifier(Config jwt) {
        return new JwtAccessTokenVerifier(JwtSettings.fromConfig(jwt));
    }
}
