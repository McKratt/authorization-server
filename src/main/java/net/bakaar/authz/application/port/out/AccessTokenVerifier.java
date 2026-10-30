package net.bakaar.authz.application.port.out;

import net.bakaar.authz.domain.AccessToken;
import net.bakaar.authz.domain.InvalidAccessTokenException;
import net.bakaar.authz.domain.Username;

/**
 * Verifies an access token against the trusted identity provider and extracts its owner.
 */
public interface AccessTokenVerifier {

    /**
     * @return the username of the token's owner
     * @throws InvalidAccessTokenException when signature, issuer, audience, expiry or username claim is invalid
     */
    Username verify(AccessToken accessToken);
}
